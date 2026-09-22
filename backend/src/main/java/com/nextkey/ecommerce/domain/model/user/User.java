package com.nextkey.ecommerce.domain.model.user;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 🔴 併發防護（DEF-136）：{@code @DynamicUpdate} 讓 Hibernate 只把「本次交易內實際被
 * setter 改動過」的欄位包進 UPDATE 語句，而非整列覆寫。避免 AuthService.login()（僅改
 * lastLoginAt）在 AdminService.updateUserStatus() 併發轉換 status 之後才 commit 時，
 * 用 login() 讀取當下的舊 status 快照把剛生效的停權/封禁結果悄悄復原（同理適用於
 * role 等其他欄位的類似讀後寫覆寫情境）。
 */
@Entity
@Table(name = "users")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    /**
     * DEF-249（Sprint 183）：全域防線——先前只在個別 Controller（如 DEF-093 修復的
     * {@code ListingController}）逐一改回傳 DTO 來避免裸實體序列化外洩本欄位，根因（本欄位本身
     * 沒有 {@code @JsonIgnore}）從未真正堵住。{@code ArticleVersionController} 後來又重蹈覆轍
     * （見 {@code ArticleVersion.createdBy}/{@code KnowledgeArticle.author} 兩條 LAZY 關聯鏈），
     * 任何持有 {@code knowledge:read} 權限者（含最低信任等級的 STORE_STAFF）皆可取得文章作者的
     * 密碼雜湊，離線暴力破解後可完整接管帳號。本專案 {@code spring.jpa.open-in-view} 為預設
     * {@code true} 且未註冊 Hibernate Jackson 模組，任何回傳裸實體、且該實體有指向 {@code User}
     * 的 LAZY 關聯的端點都會真的觸發懶載入並序列化出本欄位，而非拋出例外——因此在欄位本身補上
     * {@code @JsonIgnore}，一次性堵住所有現在及未來可能出現的同類外洩路徑，不再逐一依賴每個
     * Controller 都正確改用 DTO。
     */
    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserRole role = UserRole.BUYER;

    @Column(name = "tenant_id")
    private UUID tenantId;

    private String status;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "phone_verified")
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "kyc_status")
    private String kycStatus;

    @Column(name = "kyc_id_number_encrypted")
    private String kycIdNumberEncrypted;

    @Column(name = "kyc_id_card_front_url")
    private String kycIdCardFrontUrl;

    @Column(name = "kyc_id_card_back_url")
    private String kycIdCardBackUrl;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum UserRole {
        GUEST, BUYER, SELLER, HOST, STORE_OWNER, STORE_STAFF, ADMIN, SUPER_ADMIN, CFO
    }
}
