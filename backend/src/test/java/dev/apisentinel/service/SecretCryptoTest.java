package dev.apisentinel.service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.apisentinel.config.AppProperties;
import org.junit.jupiter.api.Test;

class SecretCryptoTest {
    private static AppProperties properties(String key) {
        return new AppProperties(new AppProperties.Security("test-jwt-secret-that-is-long-enough-for-hmac", key, 15, false),
            new AppProperties.Cors("http://localhost:5173"), new AppProperties.Monitoring(5000, 8, 90, 65536),
            new AppProperties.Notifications(5, 5));
    }

    @Test
    void encryptsSecretsWithRandomizedAuthenticatedCiphertext() {
        SecretCrypto crypto = new SecretCrypto(properties("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        String secret = "Authorization: Bearer highly-sensitive-value";
        String first = crypto.encrypt(secret);
        String second = crypto.encrypt(secret);

        assertNotEquals(first, second);
        assertFalse(first.contains("highly-sensitive-value"));
        assertEquals(secret, crypto.decrypt(first));
    }

    @Test
    void rejectsInvalidEncryptionKeyLength() {
        boolean thrown = false;
        try { new SecretCrypto(properties("YWJj")); }
        catch (IllegalStateException ex) { thrown = ex.getMessage().contains("32 bytes"); }
        assertTrue(thrown);
    }

}
