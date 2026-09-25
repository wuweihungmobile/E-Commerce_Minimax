package com.nextkey.ecommerce.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 全庫 Repository 查詢「真的在真實 PostgreSQL 執行過」的機械式掃描（DEF-272 的系統性補強）。
 *
 * <p><b>為什麼需要</b>：全庫測試絕大多數以 mock 取代 Repository，只驗證「Service 用什麼參數呼叫」，
 * 永遠不會執行 JPQL 的參數型別綁定、native SQL 的欄位與語法、{@code @Lock}／{@code @Modifying} 的交易語意。
 * DEF-272 正是這樣：兩個查詢以 {@code LocalDateTime} 綁 {@code Instant} 欄位，真實資料庫每次都拋例外，
 * 週結算單從未成功產生過，測試卻全綠。
 *
 * <p><b>做法</b>：以反射列舉每個 Repository <b>自己宣告</b>的查詢方法（繼承自 {@code JpaRepository} 的
 * 標準 CRUD 不在此列），依「方法簽名上宣告的參數型別」合成一組不會命中任何資料列的參數，在交易內對真實
 * 資料庫各執行一次並一律回滾。合成參數<b>直接取自簽名</b>，所以簽名與實體欄位型別錯配（DEF-272 的形態）、
 * 欄位／表格不存在、SQL 語法錯誤、不支援的參數型別，都會在這裡立即現形。
 *
 * <p><b>能證明什麼／不能證明什麼</b>：只證明「查詢能被資料庫接受並執行」，<b>不證明結果語意正確</b>
 * （合成參數不命中任何資料）——語意要靠各功能自己的真實 DB 測試（見
 * {@code SettlementPeriodBoundaryIntegrationTest}、{@code AnalyticsRealDbIntegrationTest}）。
 * 以實體物件為參數的方法無法合成，會被列入「略過」清單，而斷言要求略過清單為空——新增這類方法的人必須有意識地處理，
 * 不能讓查詢靜默地沒被掃到。
 *
 * <p><b>兩輪</b>：第一輪全部參數非 null；第二輪把所有「可為 null」的參數改傳 null。後者專抓可選篩選條件
 * {@code (:x IS NULL OR ...)} 的型別推斷問題（DEF-277 的第二層：null 綁成 {@code bytea}，
 * {@code function lower(bytea) does not exist}）。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-REPO-EXEC: 每個 Repository 宣告的查詢方法都能在真實資料庫執行（DEF-272 系統性掃描）")
class RepositoryQueryExecutionIntegrationTest {

    /** 表示「這個參數型別無法從簽名合成」。 */
    private static final Object UNSUPPORTED = new Object();

    @Autowired private ApplicationContext context;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("所有 Repository 宣告的查詢方法在真實 PostgreSQL 執行不拋例外")
    void everyDeclaredRepositoryMethodExecutesAgainstRealDatabase() {
        final List<String> failures = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
        int executed = 0;

        // Hibernate 會把先前執行帶進來的真實值型別記在查詢計畫上：同一個查詢只要「先」用非 null 值執行過，之後傳 null
        // 就不會再出問題——正式環境上該缺陷因此變成「JVM 第一次呼叫剛好不帶條件才爆」的間歇性缺陷。
        // 所以 (1) null 輪排在非 null 輪之前，(2) 開跑前清掉查詢計畫快取——`mvn verify` 是所有整合測試共用同一個 JVM，
        // 別的測試可能已經替我們「教會」了計畫。突變驗證（拿掉 DEF-277 的 CAST）證實：少了任一個，本測試都抓不到。
        entityManagerFactory.unwrap(SessionFactoryImplementor.class).getQueryEngine().getInterpretationCache().close();
        for (final boolean nullPass : new boolean[] {true, false}) {
            for (final Class<?> repositoryInterface : repositoryInterfaces()) {
                final Object bean = context.getBean(repositoryInterface);
                final Method[] methods = repositoryInterface.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
                for (final Method method : methods) {
                    if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    final String label = repositoryInterface.getSimpleName() + "#" + method.getName()
                            + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).toList();
                    final Object[] args = synthesizeArguments(method, nullPass);
                    if (args == null) {
                        if (!nullPass) {
                            skipped.add(label);
                        }
                        continue;
                    }
                    if (nullPass && !hasNullableParameter(method)) {
                        continue;
                    }
                    executed++;
                    final Throwable failure = execute(bean, method, args);
                    if (failure != null) {
                        failures.add(label + (nullPass ? " [nulls]" : "") + " -> " + describe(failure));
                    }
                }
            }
        }

        System.out.println("[REPO-EXEC] executed=" + executed + " skipped=" + skipped.size()
                + " failures=" + failures.size());
        skipped.forEach(s -> System.out.println("[REPO-EXEC][SKIPPED] " + s));
        failures.forEach(f -> System.out.println("[REPO-EXEC][FAIL] " + f));

        assertThat(failures).as("以下 Repository 方法在真實資料庫執行失敗").isEmpty();
        assertThat(skipped).as("以下方法的參數型別無法合成、沒有被執行——請擴充 dummy() 或有意識地排除").isEmpty();
        // 防止反射什麼都沒找到而「空轉綠燈」：目前約 750 次（378 個方法，含 null 輪）
        assertThat(executed).as("實際執行的查詢次數").isGreaterThan(500);
    }

    private static boolean hasNullableParameter(final Method method) {
        return Arrays.stream(method.getParameterTypes()).anyMatch(RepositoryQueryExecutionIntegrationTest::isNullable);
    }

    private static boolean isNullable(final Class<?> type) {
        return !type.isPrimitive() && type != Pageable.class && type != Sort.class
                && !Collection.class.isAssignableFrom(type);
    }

    private List<Class<?>> repositoryInterfaces() {
        final List<Class<?>> result = new ArrayList<>();
        for (final Object bean : context.getBeansOfType(Repository.class).values()) {
            for (final Class<?> candidate : AopProxyUtils.proxiedUserInterfaces(bean)) {
                if (candidate.getName().startsWith("com.nextkey.ecommerce.")
                        && Repository.class.isAssignableFrom(candidate)) {
                    result.add(candidate);
                }
            }
        }
        result.sort(Comparator.comparing(Class::getName));
        return result;
    }

    /** 在交易內執行並一律回滾；回傳 null 代表成功，否則回傳最內層例外以外的原始例外。 */
    private Throwable execute(final Object bean, final Method method, final Object[] args) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                try {
                    consume(method.invoke(bean, args));
                } catch (InvocationTargetException e) {
                    throw sneaky(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } finally {
                    status.setRollbackOnly();
                }
            });
            return null;
        } catch (Throwable t) {
            // 「結果不唯一」發生在查詢已被資料庫成功執行之後（例如 null 參數讓 `IS NULL` 命中許多列、
            // 而方法回傳 Optional）：證明查詢可執行，只是資料形狀不符，不是本測試要抓的缺陷。
            if (t instanceof IncorrectResultSizeDataAccessException) {
                return null;
            }
            return t;
        }
    }

    private static void consume(final Object result) {
        if (result instanceof Stream<?> stream) {
            try (stream) {
                stream.forEach(row -> { });
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(final Throwable t) throws T {
        throw (T) t;
    }

    private static String describe(final Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        final String message = String.valueOf(root.getMessage()).replaceAll("\\s+", " ");
        return failure.getClass().getSimpleName() + " / " + root.getClass().getSimpleName() + ": "
                + (message.length() > 260 ? message.substring(0, 260) + "…" : message);
    }

    /** 回傳合成的參數陣列；任一參數無法合成則回傳 null（呼叫端列入略過清單）。 */
    private static Object[] synthesizeArguments(final Method method, final boolean nullOptionals) {
        final Class<?>[] types = method.getParameterTypes();
        final Type[] generics = method.getGenericParameterTypes();
        final Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            final Object value = dummy(types[i], generics[i]);
            if (value == UNSUPPORTED) {
                return null;
            }
            args[i] = nullOptionals && isNullable(types[i]) ? null : value;
        }
        return args;
    }

    private static Object dummy(final Class<?> type, final Type generic) {
        if (type == UUID.class) {
            return UUID.randomUUID();
        }
        if (type == String.class) {
            return "__repo_exec_smoke__";
        }
        if (type == Instant.class) {
            return Instant.parse("2000-01-01T00:00:00Z");
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2000, 1, 1, 0, 0);
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2000, 1, 1);
        }
        if (type == LocalTime.class) {
            return LocalTime.NOON;
        }
        if (type == OffsetDateTime.class) {
            return OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (type == Long.class || type == long.class) {
            return 1L;
        }
        if (type == Integer.class || type == int.class) {
            return 1;
        }
        if (type == Short.class || type == short.class) {
            return (short) 1;
        }
        if (type == Double.class || type == double.class) {
            return 1.0d;
        }
        if (type == Float.class || type == float.class) {
            return 1.0f;
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type == Pageable.class) {
            return PageRequest.of(0, 1);
        }
        if (type == Sort.class) {
            return Sort.unsorted();
        }
        if (Collection.class.isAssignableFrom(type)) {
            return dummyCollection(type, generic);
        }
        return UNSUPPORTED;
    }

    private static Object dummyCollection(final Class<?> collectionType, final Type generic) {
        Class<?> elementType = null;
        if (generic instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[0];
            if (argument instanceof WildcardType wildcard) {
                argument = wildcard.getUpperBounds()[0];
            }
            if (argument instanceof Class<?> clazz) {
                elementType = clazz;
            }
        }
        if (elementType == null) {
            return UNSUPPORTED;
        }
        final Object element = dummy(elementType, elementType);
        if (element == UNSUPPORTED) {
            return UNSUPPORTED;
        }
        return Set.class.isAssignableFrom(collectionType) ? Set.of(element) : List.of(element);
    }
}
