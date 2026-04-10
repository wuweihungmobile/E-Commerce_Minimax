package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.user.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<OAuthAccount> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}