package dev.apisentinel.service;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UrlSecurityValidator {
    private static final List<String> BLOCKED_SUFFIXES = List.of(
        ".localhost", ".local", ".internal", ".home.arpa", ".lan", ".test", ".invalid", ".example", ".onion"
    );
    private static final long DNS_CACHE_NANOS = 2_000_000_000L;
    private static final int DNS_CACHE_LIMIT = 1024;
    private final HostResolver resolver;
    private final Map<String, CachedAddresses> addressCache = new LinkedHashMap<>(128, 0.75f, true);

    public UrlSecurityValidator(HostResolver resolver) { this.resolver = resolver; }

    public ValidatedTarget validate(String rawUrl, boolean httpsOnly) throws IOException {
        final URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (Exception ex) {
            throw new BlockedTargetException(ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || (httpsOnly && !scheme.equals("https"))) {
            throw new BlockedTargetException();
        }
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getHost() == null) {
            throw new BlockedTargetException();
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) throw new BlockedTargetException();
        String host = normalizeHost(uri.getHost());
        if (isBlockedName(host)) throw new BlockedTargetException();
        try {
            resolvePublicAddresses(host);
        } catch (BlockedDnsException ex) {
            throw new BlockedTargetException(ex);
        }
        return new ValidatedTarget(uri, host, hasMeaningfulPath(uri));
    }

    /** Called again by OkHttp at connect time; its route candidates are restricted to this validated DNS answer. */
    public List<InetAddress> resolvePublicAddresses(String host) throws UnknownHostException {
        String normalized = normalizeHost(host);
        if (isBlockedName(normalized)) throw new BlockedDnsException();
        long now = System.nanoTime();
        synchronized (addressCache) {
            CachedAddresses cached = addressCache.get(normalized);
            if (cached != null && now - cached.resolvedAtNanos() < DNS_CACHE_NANOS) return cached.addresses();
            if (cached != null) addressCache.remove(normalized);
        }
        List<InetAddress> addresses = resolver.resolve(normalized);
        if (addresses == null || addresses.isEmpty()) throw new UnknownHostException("No address available");
        for (InetAddress address : addresses) {
            if (address == null || !isGloballyRoutable(address)) throw new BlockedDnsException();
        }
        List<InetAddress> result = List.copyOf(addresses);
        synchronized (addressCache) {
            addressCache.put(normalized, new CachedAddresses(result, now));
            while (addressCache.size() > DNS_CACHE_LIMIT) {
                String eldest = addressCache.keySet().iterator().next();
                addressCache.remove(eldest);
            }
        }
        return result;
    }

    public static boolean isGloballyRoutable(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) return isPublicIpv4(b);
        if (!(address instanceof Inet6Address) || b.length != 16) return false;
        int first = b[0] & 0xff;
        // Only global-unicast 2000::/3 is accepted; local, unspecified, and multicast scopes are excluded.
        if ((first & 0xe0) != 0x20) return false;
        if (startsWith(b, new int[]{0x20, 0x01, 0x0d, 0xb8})) return false; // documentation
        if (startsWith(b, new int[]{0x20, 0x01, 0x00, 0x00})) return false; // Teredo
        if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x02) return false; // 6to4
        return true;
    }

    private static boolean startsWith(byte[] address, int[] prefix) {
        for (int i = 0; i < prefix.length; i++) if ((address[i] & 0xff) != prefix[i]) return false;
        return true;
    }

    private static boolean isPublicIpv4(byte[] b) {
        if (b.length != 4) return false;
        int a = b[0] & 0xff, second = b[1] & 0xff, third = b[2] & 0xff, fourth = b[3] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && second >= 64 && second <= 127) return false; // carrier-grade NAT / Alibaba metadata
        if (a == 169 && second == 254) return false; // link-local / cloud metadata
        if (a == 172 && second >= 16 && second <= 31) return false;
        if (a == 192 && second == 168) return false;
        if (a == 192 && second == 0 && (third == 0 || third == 2)) return false;
        if (a == 192 && second == 88 && third == 99) return false;
        if (a == 198 && (second == 18 || second == 19)) return false; // benchmarking
        if (a == 198 && second == 51 && third == 100) return false;
        if (a == 203 && second == 0 && third == 113) return false;
        if (a == 168 && second == 63 && third == 129 && fourth == 16) return false; // Azure platform IP
        return true;
    }

    private static boolean isBlockedName(String host) {
        if (host.isBlank() || host.equals("localhost") || host.equals("metadata.google.internal")
            || host.equals("instance-data.ec2.internal") || host.equals("metadata.azure.internal")
            || host.equals("metadata.tencentyun.com") || host.equals("100.100.100.200")) return true;
        for (String suffix : BLOCKED_SUFFIXES) if (host.endsWith(suffix)) return true;
        return false;
    }

    private static String normalizeHost(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) normalized = normalized.substring(1, normalized.length() - 1);
        while (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static boolean hasMeaningfulPath(URI uri) {
        String path = uri.getRawPath();
        return path != null && !path.isBlank() && !path.equals("/");
    }

    private record CachedAddresses(List<InetAddress> addresses, long resolvedAtNanos) { }

    public record ValidatedTarget(URI uri, String host, boolean hasPath) {
        public String display() {
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme + "://" + uri.getRawAuthority() + (hasPath ? "/…" : "");
        }
    }
}
