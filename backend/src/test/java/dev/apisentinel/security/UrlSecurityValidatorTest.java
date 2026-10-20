package dev.apisentinel.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.apisentinel.service.BlockedTargetException;
import dev.apisentinel.service.UrlSecurityValidator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UrlSecurityValidatorTest {
    private static InetAddress ip(String address) {
        try { return InetAddress.getByName(address); }
        catch (UnknownHostException ex) { throw new AssertionError(ex); }
    }

    @Test
    void permitsPublicWebTargetsButNeverReturnsPathOrQuerySecrets() throws Exception {
        UrlSecurityValidator validator = new UrlSecurityValidator(host -> List.of(ip("8.8.8.8")));
        var target = validator.validate("https://api.acme.net/v1/health?access_token=secret-value", false);
        assertEquals("api.acme.net", target.host());
        assertTrue(target.hasPath());
        assertFalse(target.display().contains("secret-value"));
        assertFalse(target.display().contains("/v1/health"));
    }

    @Test
    void rejectsLoopbackPrivateMetadataAndMixedDnsAnswers() {
        UrlSecurityValidator localhost = new UrlSecurityValidator(host -> List.of(ip("127.0.0.1")));
        assertThrows(BlockedTargetException.class, () -> localhost.validate("http://localhost/health", false));
        assertThrows(BlockedTargetException.class, () -> localhost.validate("http://127.0.0.1/health", false));

        UrlSecurityValidator privateDns = new UrlSecurityValidator(host -> List.of(ip("10.12.0.4")));
        assertThrows(BlockedTargetException.class, () -> privateDns.validate("https://private.acme.net", false));

        UrlSecurityValidator mixed = new UrlSecurityValidator(host -> List.of(ip("8.8.8.8"), ip("169.254.169.254")));
        assertThrows(BlockedTargetException.class, () -> mixed.validate("https://mixed.acme.net", false));
    }

    @Test
    void requiresHttpsForWebhookTargetsAndRejectsUnsafeSchemesAndUserInfo() {
        UrlSecurityValidator validator = new UrlSecurityValidator(host -> List.of(ip("1.1.1.1")));
        assertThrows(BlockedTargetException.class, () -> validator.validate("http://hooks.acme.net/callback", true));
        assertThrows(BlockedTargetException.class, () -> validator.validate("file:///etc/passwd", false));
        assertThrows(BlockedTargetException.class, () -> validator.validate("https://user:password@hooks.acme.net/callback", true));
        assertThrows(BlockedTargetException.class, () -> validator.validate("http://metadata.google.internal/latest/meta-data", false));
    }

    @Test
    void pinsTheRecentlyValidatedDnsAnswerToPreventRebindingBetweenValidationAndConnect() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        UrlSecurityValidator validator = new UrlSecurityValidator(host ->
            resolutions.incrementAndGet() == 1 ? List.of(ip("8.8.8.8")) : List.of(ip("127.0.0.1")));
        validator.validate("https://api.acme.net/health", false);
        assertEquals(List.of(ip("8.8.8.8")), validator.resolvePublicAddresses("api.acme.net"));
        assertEquals(1, resolutions.get());
    }
}
