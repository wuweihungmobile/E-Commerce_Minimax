package com.nextkey.ecommerce.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;

/**
 * StorageService 單元測試（Sprint 169，DEF-221）。
 *
 * <p>{@code buildObjectName} 先前直接把 {@code MultipartFile#getOriginalFilename()}
 * （完全由呼叫端在 multipart 請求中自行宣告，與本機檔案系統無關）拼進物件鍵，未做任何過濾。
 * 本測試以 Mockito 取代內部 {@code MinioClient}，驗證實際送往儲存層的物件鍵。
 */
@DisplayName("StorageService 單元測試（DEF-221：上傳檔名路徑穿越防護）")
class StorageServiceTest {

    private StorageService storageService;
    private MinioClient minioClient;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(
                "http://localhost:1", "test-access-key", "test-secret-key",
                "test-bucket", "us-east-1", 3600);
        minioClient = mock(MinioClient.class);
        ReflectionTestUtils.setField(storageService, "minioClient", minioClient);
    }

    @Test
    @DisplayName("uploadFile：檔名含路徑穿越序列時，物件鍵不得帶出租戶前綴外的路徑片段")
    void uploadFile_pathTraversalFileName_objectKeyStaysWithinTenantPrefix() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String maliciousFileName = "../../other-tenant/evil.jpg";
        InputStream inputStream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

        storageService.uploadFile(tenantId, maliciousFileName, inputStream, 4, "image/jpeg");

        String objectName = capturePutObjectName();

        assertThat(objectName).startsWith(tenantId + "/");
        String afterTenantPrefix = objectName.substring((tenantId + "/").length());
        assertThat(afterTenantPrefix).doesNotContain("/");
        assertThat(afterTenantPrefix).doesNotContain("..");
    }

    @Test
    @DisplayName("uploadFile：Windows 風格反斜線路徑穿越序列同樣被擋下")
    void uploadFile_backslashPathTraversalFileName_objectKeyStaysWithinTenantPrefix() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String maliciousFileName = "..\\..\\other-tenant\\evil.jpg";
        InputStream inputStream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

        storageService.uploadFile(tenantId, maliciousFileName, inputStream, 4, "image/jpeg");

        String objectName = capturePutObjectName();

        String afterTenantPrefix = objectName.substring((tenantId + "/").length());
        assertThat(afterTenantPrefix).doesNotContain("/");
        assertThat(afterTenantPrefix).doesNotContain("\\");
        assertThat(afterTenantPrefix).doesNotContain("..");
    }

    @Test
    @DisplayName("uploadFile：正常中文檔名不受影響（不可誤傷合法檔名）")
    void uploadFile_chineseFileName_preservedAsIs() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String fileName = "假期照片.jpg";
        InputStream inputStream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

        storageService.uploadFile(tenantId, fileName, inputStream, 4, "image/jpeg");

        String objectName = capturePutObjectName();

        assertThat(objectName).endsWith("-" + fileName);
    }

    private String capturePutObjectName() throws Exception {
        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        return captor.getValue().object();
    }
}
