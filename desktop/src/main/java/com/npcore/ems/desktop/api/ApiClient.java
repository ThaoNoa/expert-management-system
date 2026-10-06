package com.npcore.ems.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gọi REST API của máy chủ EMS. Tự gắn Bearer token, tự gọi /auth/refresh một lần khi gặp 401.
 * Thread-safe; mọi lời gọi đều chặn (blocking) nên phải chạy ngoài JavaFX thread (xem ui.fx.Async).
 */
public final class ApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final HttpClient http;
    private volatile String accessToken;
    private volatile String refreshToken;
    private final Object refreshLock = new Object();
    private Runnable onSessionExpired = () -> {};

    public ApiClient(String serverUrl) {
        String s = serverUrl.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        this.baseUrl = s + "/api/v1";
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public void setOnSessionExpired(Runnable r) {
        this.onSessionExpired = r;
    }

    public void setTokens(String access, String refresh) {
        this.accessToken = access;
        this.refreshToken = refresh;
    }

    public String refreshToken() {
        return refreshToken;
    }

    // ------------------------------------------------------------------ JSON

    public <T> T get(String path, Class<T> type) {
        return Json.MAPPER.convertValue(send("GET", path, null), type);
    }

    public <T> T get(String path, TypeReference<T> type) {
        return Json.MAPPER.convertValue(send("GET", path, null), type);
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return convert(send("POST", path, body), type);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        JsonNode n = send("POST", path, body);
        return n == null ? null : Json.MAPPER.convertValue(n, type);
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return convert(send("PUT", path, body), type);
    }

    public void post(String path, Object body) {
        send("POST", path, body);
    }

    public void delete(String path) {
        send("DELETE", path, null);
    }

    private static <T> T convert(JsonNode n, Class<T> type) {
        if (n == null || type == Void.class) return null;
        return Json.MAPPER.convertValue(n, type);
    }

    private JsonNode send(String method, String path, Object body) {
        HttpResponse<byte[]> res = execute(() -> {
            HttpRequest.Builder b = request(path);
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(writeJson(body)));
            }
            return b.build();
        });
        if (res.body() == null || res.body().length == 0) return null;
        try {
            return Json.MAPPER.readTree(res.body());
        } catch (IOException e) {
            throw new ApiException(res.statusCode(), "BAD_RESPONSE", "Phản hồi không hợp lệ từ máy chủ", null);
        }
    }

    // ------------------------------------------------------------------ files

    /** Gửi multipart/form-data: các trường text + (tuỳ chọn) một file ở trường "file". */
    public <T> T upload(String path, Map<String, String> fields, Path file, Class<T> type) {
        String boundary = "----ems" + UUID.randomUUID();
        byte[] payload = multipart(boundary, fields, file);
        HttpResponse<byte[]> res = execute(() -> request(path)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build());
        try {
            return res.body().length == 0 ? null : Json.MAPPER.readValue(res.body(), type);
        } catch (IOException e) {
            throw new ApiException(res.statusCode(), "BAD_RESPONSE", "Phản hồi không hợp lệ từ máy chủ", null);
        }
    }

    /** Tải file về thư mục đích; trả về đường dẫn file đã lưu (tên lấy từ Content-Disposition). */
    public Path download(String path, Path targetDir, String fallbackName) {
        HttpResponse<byte[]> res = execute(() -> request(path).GET().build());
        String name = fileName(res.headers().firstValue("Content-Disposition")).orElse(fallbackName);
        Path target = uniquePath(targetDir.resolve(sanitize(name)));
        try {
            Files.write(target, res.body());
        } catch (IOException e) {
            throw ApiException.connection("Không ghi được file: " + e.getMessage());
        }
        return target;
    }

    // ------------------------------------------------------------------ core

    private interface RequestFactory {
        HttpRequest build();
    }

    private HttpResponse<byte[]> execute(RequestFactory factory) {
        HttpResponse<byte[]> res = raw(factory.build());
        if (res.statusCode() == 401 && refreshToken != null && tryRefresh()) {
            res = raw(factory.build());
        }
        if (res.statusCode() == 401 && accessToken != null) {
            onSessionExpired.run();
        }
        if (res.statusCode() >= 400) throw toException(res);
        return res;
    }

    private HttpResponse<byte[]> raw(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (ConnectException e) {
            throw ApiException.connection("Không kết nối được máy chủ " + baseUrl.replace("/api/v1", ""));
        } catch (HttpTimeoutException e) {
            throw ApiException.connection("Máy chủ không phản hồi (quá thời gian chờ)");
        } catch (IOException e) {
            throw ApiException.connection("Lỗi kết nối: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.connection("Đã huỷ");
        }
    }

    private boolean tryRefresh() {
        synchronized (refreshLock) {
            String current = refreshToken;
            if (current == null) return false;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/refresh")).timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(writeJson(Map.of("refreshToken", current))))
                        .build();
                HttpResponse<byte[]> res = raw(req);
                if (res.statusCode() != 200) {
                    accessToken = null;
                    refreshToken = null;
                    return false;
                }
                JsonNode n = Json.MAPPER.readTree(res.body());
                accessToken = n.path("accessToken").asText();
                refreshToken = n.path("refreshToken").asText();
                return true;
            } catch (IOException | ApiException e) {
                return false;
            }
        }
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(TIMEOUT)
                .header("Accept", "application/json, application/problem+json, */*");
        String token = accessToken;
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private static ApiException toException(HttpResponse<byte[]> res) {
        try {
            JsonNode n = Json.MAPPER.readTree(res.body());
            Map<String, String> fields = new LinkedHashMap<>();
            n.path("errors").forEach(e -> fields.put(e.path("field").asText(), e.path("message").asText()));
            String detail = n.path("detail").asText(null);
            return new ApiException(res.statusCode(), n.path("code").asText("HTTP_" + res.statusCode()),
                    detail != null ? detail : defaultMessage(res.statusCode()), fields);
        } catch (IOException | RuntimeException e) {
            return new ApiException(res.statusCode(), "HTTP_" + res.statusCode(), defaultMessage(res.statusCode()), null);
        }
    }

    private static String defaultMessage(int status) {
        return switch (status) {
            case 401 -> "Phiên đăng nhập đã hết hạn";
            case 403 -> "Không có quyền thực hiện thao tác này";
            case 404 -> "Không tìm thấy dữ liệu";
            default -> "Lỗi máy chủ (" + status + ")";
        };
    }

    private static byte[] writeJson(Object body) {
        try {
            return Json.MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static byte[] multipart(String boundary, Map<String, String> fields, Path file) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String crlf = "\r\n";
            for (Map.Entry<String, String> f : fields.entrySet()) {
                if (f.getValue() == null || f.getValue().isBlank()) continue;
                out.write(("--" + boundary + crlf + "Content-Disposition: form-data; name=\"" + f.getKey() + "\"" + crlf
                        + "Content-Type: text/plain; charset=UTF-8" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
                out.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                out.write(crlf.getBytes(StandardCharsets.UTF_8));
            }
            if (file != null) {
                String name = file.getFileName().toString().replace("\"", "");
                String type = Optional.ofNullable(Files.probeContentType(file)).orElse("application/octet-stream");
                out.write(("--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"" + name
                        + "\"" + crlf + "Content-Type: " + type + crlf + crlf).getBytes(StandardCharsets.UTF_8));
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(out);
                }
                out.write(crlf.getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw ApiException.connection("Không đọc được file: " + e.getMessage());
        }
    }

    private static final Pattern FILENAME_STAR = Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME = Pattern.compile("filename=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    static Optional<String> fileName(Optional<String> header) {
        if (header.isEmpty()) return Optional.empty();
        Matcher m = FILENAME_STAR.matcher(header.get());
        if (m.find()) return Optional.of(URLDecoder.decode(m.group(1), StandardCharsets.UTF_8));
        m = FILENAME.matcher(header.get());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static Path uniquePath(Path p) {
        if (!Files.exists(p)) return p;
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        String ext = dot > 0 ? n.substring(dot) : "";
        for (int i = 1; ; i++) {
            Path c = p.resolveSibling(base + " (" + i + ")" + ext);
            if (!Files.exists(c)) return c;
        }
    }

    /** Mã hoá giá trị cho query string. */
    public static String q(Object value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    /** Ghép query string, bỏ các tham số null/rỗng. */
    public static String query(Map<String, ?> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (v == null || String.valueOf(v).isBlank()) return;
            sb.append(sb.isEmpty() ? '?' : '&').append(k).append('=').append(q(v));
        });
        return sb.toString();
    }

    /** Copy file (dùng khi cần lưu tạm). */
    static void copy(Path from, Path to) throws IOException {
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
}
