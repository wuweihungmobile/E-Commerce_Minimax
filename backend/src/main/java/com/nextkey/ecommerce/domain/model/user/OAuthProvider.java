package com.nextkey.ecommerce.domain.model.user;

/**
 * OAuth 提供者枚舉
 */
public enum OAuthProvider {
    GOOGLE("google", "Google"),
    GITHUB("github", "GitHub");

    private final String providerId;
    private final String displayName;

    OAuthProvider(String providerId, String displayName) {
        this.providerId = providerId;
        this.displayName = displayName;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OAuthProvider fromProviderId(final String providerId) {
        for (OAuthProvider provider : values()) {
            if (provider.providerId.equalsIgnoreCase(providerId)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown OAuth provider: " + providerId);
    }
}