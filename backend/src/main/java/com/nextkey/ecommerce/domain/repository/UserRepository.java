package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.user.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndStatus(String email, String status);

    List<User> findByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);

    /**
     * 併發防護（DEF-136，UserPrivacyService.deleteMyAccount）：以 {@code SELECT ... FOR UPDATE}
     * 鎖住此使用者列。PostgreSQL 對 {@code orders.user_id}/{@code bookings.user_id} 的外鍵約束，
     * 會讓併發新增訂單/訂房的 INSERT 隱含需要此列的鎖，藉此讓「檢查有無未結案訂單/訂房」與
     * 「匿名化並提交」之間，至少能擋下「鎖先於新訂單建立」這個方向的競態（鎖後於新訂單建立的
     * 反方向——訂單已建立才輪到本交易取得鎖——需要訂單/訂房建立端自行驗證使用者帳號狀態才能
     * 完全杜絕，屬於另一個範圍更大的架構性待辦，此處僅縮小已知的競態窗口，非宣稱完全消除）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
