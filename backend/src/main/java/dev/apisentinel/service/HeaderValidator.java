package dev.apisentinel.service;

import dev.apisentinel.web.ApiException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HeaderValidator {
    private static final Set<String> FORBIDDEN = Set.of("host", "content-length", "transfer-encoding", "connection",
        "proxy-authorization", "proxy-authenticate", "expect", "te", "trailer", "upgrade");
    private HeaderValidator() { }

    public static void validate(Map<String, String> headers) {
        if (headers == null) return;
        if (headers.size() > 20) throw ApiException.badRequest("INVALID_HEADERS", "At most 20 request headers are allowed");
        int bytes = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}") || value == null
                || value.length() > 4096 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
                || FORBIDDEN.contains(name.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("INVALID_HEADERS", "A request header is invalid or not permitted");
            }
            bytes += name.length() + value.length();
            if (bytes > 16_384) throw ApiException.badRequest("INVALID_HEADERS", "Combined request headers exceed the size limit");
        }
    }
}
