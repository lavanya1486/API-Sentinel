package dev.apisentinel.service;

import dev.apisentinel.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

@Component
public class SafeHttpClient {
    private final UrlSecurityValidator urlSecurity;
    private final OkHttpClient baseClient;
    private final int maxResponseBytes;

    public SafeHttpClient(UrlSecurityValidator urlSecurity, AppProperties properties) {
        this.urlSecurity = urlSecurity;
        this.maxResponseBytes = Math.max(1024, properties.monitoring().maxResponseBytes());
        this.baseClient = new OkHttpClient.Builder()
            // OkHttp connects only to the DNS answers checked here; the original hostname remains
            // the HTTP Host/SNI value for correct TLS validation.
            .dns(host -> urlSecurity.resolvePublicAddresses(host))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build();
    }

    public HttpResult check(String rawUrl, String method, Map<String, String> headers, int timeoutMs) throws IOException {
        UrlSecurityValidator.ValidatedTarget target = urlSecurity.validate(rawUrl, false);
        return execute(target.uri(), method, headers, timeoutMs, null);
    }

    public HttpResult sendWebhook(String rawUrl, String payloadJson, int timeoutMs) throws IOException {
        UrlSecurityValidator.ValidatedTarget target = urlSecurity.validate(rawUrl, true);
        return execute(target.uri(), "POST", Map.of("Content-Type", "application/json"), timeoutMs,
            payloadJson);
    }

    private HttpResult execute(URI uri, String method, Map<String, String> headers, int timeoutMs,
                               String bodyText) throws IOException {
        HeaderValidator.validate(headers);
        String verb = method.toUpperCase(java.util.Locale.ROOT);
        RequestBody body = null;
        if (bodyText != null) {
            body = RequestBody.create(bodyText, MediaType.get("application/json; charset=utf-8"));
        } else if (verb.equals("POST") || verb.equals("PUT") || verb.equals("PATCH")) {
            // These HTTP verbs require a body in OkHttp. Sentinel intentionally sends an empty body.
            body = RequestBody.create(new byte[0], null);
        }

        Request.Builder builder = new Request.Builder().url(uri.toASCIIString());
        if (headers != null) headers.forEach(builder::header);
        builder.method(verb, body);
        int timeout = Math.max(500, Math.min(timeoutMs, 30_000));
        OkHttpClient client = baseClient.newBuilder()
            .connectTimeout(Math.min(timeout, 5_000), TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .callTimeout(timeout, TimeUnit.MILLISECONDS)
            .build();

        try (Response response = client.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                // Never retain response content. Stop after a strict byte cap and close the stream.
                try (java.io.InputStream in = responseBody.byteStream()) {
                    in.readNBytes(maxResponseBytes);
                }
            }
            return new HttpResult(response.code());
        }
    }

    public record HttpResult(int statusCode) { }
}
