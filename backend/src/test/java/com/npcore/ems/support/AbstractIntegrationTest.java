package com.npcore.ems.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Nền cho integration test: khởi động toàn bộ ứng dụng trên cổng ngẫu nhiên, gọi REST thật qua HTTP.
 *
 * <p>DB: nếu có biến môi trường EMS_TEST_DB_URL (/USER/PASSWORD) thì dùng DB đó (đã migrate sẵn);
 * nếu không, nạp {@code TestcontainersSupport} bằng reflection (chỉ có trong build profile full) để chạy
 * PostgreSQL trong container. DB có thể dùng chung giữa các lần chạy → mọi test tự tạo dữ liệu tên duy nhất.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String API = "/api/v1";
    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "Admin@12345";
    protected static final String DEFAULT_PASSWORD = "Passw0rd!x";

    private static final Map<String, String> TOKEN_CACHE = new ConcurrentHashMap<>();

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper mapper;

    private RestTemplate rest;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("EMS_TEST_DB_URL");
        String user;
        String password;
        if (url != null && !url.isBlank()) {
            user = envOr("EMS_TEST_DB_USER", "postgres");
            password = envOr("EMS_TEST_DB_PASSWORD", "postgres");
        } else {
            String[] c = startContainer();
            url = c[0];
            user = c[1];
            password = c[2];
        }
        final String u = url;
        final String us = user;
        final String pw = password;
        registry.add("spring.datasource.url", () -> u);
        registry.add("spring.datasource.username", () -> us);
        registry.add("spring.datasource.password", () -> pw);
    }

    private static String envOr(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }

    private static String[] startContainer() {
        try {
            Class<?> support = Class.forName("com.npcore.ems.support.TestcontainersSupport");
            Method start = support.getMethod("start");
            Object result = start.invoke(null);
            if (result instanceof String[] arr && arr.length == 3) return arr;
            throw new IllegalStateException("TestcontainersSupport.start() phải trả về {url, user, password}");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Không có EMS_TEST_DB_URL và không có Testcontainers (build offline). "
                    + "Đặt EMS_TEST_DB_URL/EMS_TEST_DB_USER/EMS_TEST_DB_PASSWORD trỏ tới DB đã migrate.", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Không khởi động được PostgreSQL container", e);
        }
    }

    // ------------------------------------------------------------------ HTTP client

    protected RestTemplate rest() {
        if (rest == null) {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1).build();
            RestTemplate t = new RestTemplate(new JdkClientHttpRequestFactory(client));
            t.setErrorHandler(new ResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) {
                    return false;                     // test tự kiểm tra status
                }

                @Override
                public void handleError(java.net.URI url, HttpMethod method, ClientHttpResponse response) {
                    // không ném lỗi
                }
            });
            rest = t;
        }
        return rest;
    }

    protected String url(String path) {
        return "http://localhost:" + port + (path.startsWith("/api") ? path : API + path);
    }

    protected HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON, MediaType.ALL));
        return h;
    }

    protected ResponseEntity<String> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders h = headers(token);
        String payload = null;
        if (body != null) {
            h.setContentType(MediaType.APPLICATION_JSON);
            try {
                payload = body instanceof String s ? s : mapper.writeValueAsString(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return rest().exchange(url(path), method, new HttpEntity<>(payload, h), String.class);
    }

    protected ResponseEntity<String> get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    protected ResponseEntity<String> post(String path, String token, Object body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    protected ResponseEntity<String> put(String path, String token, Object body) {
        return exchange(HttpMethod.PUT, path, token, body);
    }

    protected ResponseEntity<String> delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, token, null);
    }

    protected ResponseEntity<byte[]> getBytes(String path, String token) {
        return rest().exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers(token)), byte[].class);
    }

    /** Gọi API, kiểm tra status và trả body JSON. */
    protected JsonNode getJson(String path, String token) {
        return expect(get(path, token), HttpStatus.OK);
    }

    protected JsonNode postJson(String path, String token, Object body, HttpStatus expected) {
        return expect(post(path, token, body), expected);
    }

    protected JsonNode putJson(String path, String token, Object body) {
        return expect(put(path, token, body), HttpStatus.OK);
    }

    protected JsonNode expect(ResponseEntity<String> response, HttpStatus expected) {
        assertThat(response.getStatusCode().value())
                .as("HTTP status, body=%s", response.getBody())
                .isEqualTo(expected.value());
        return json(response);
    }

    /** Kiểm tra status + mã lỗi ("code") của ProblemDetail. */
    protected JsonNode expectError(ResponseEntity<String> response, HttpStatus status, String code) {
        JsonNode body = expect(response, status);
        assertThat(body.path("code").asText()).as("error code, body=%s", response.getBody()).isEqualTo(code);
        return body;
    }

    protected JsonNode json(ResponseEntity<String> response) {
        String b = response.getBody();
        if (b == null || b.isBlank()) return mapper.nullNode();
        try {
            return mapper.readTree(b);
        } catch (IOException e) {
            throw new UncheckedIOException("Body không phải JSON: " + b, e);
        }
    }

    // ------------------------------------------------------------------ multipart

    protected static ByteArrayResource file(String fileName, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    protected static ByteArrayResource file(String fileName, String content) {
        return file(fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Upload multipart: part "file" + các field dạng text (bỏ qua giá trị null).
     */
    protected ResponseEntity<String> uploadMultipart(String path, String token, String fileName, byte[] content,
                                                     Map<String, ?> fields) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(contentTypeOf(fileName));
        form.add("file", new HttpEntity<>(file(fileName, content), partHeaders));
        if (fields != null) {
            fields.forEach((k, v) -> {
                if (v != null) form.add(k, String.valueOf(v));
            });
        }
        HttpHeaders h = headers(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest().exchange(url(path), HttpMethod.POST, new HttpEntity<>(form, h), String.class);
    }

    protected ResponseEntity<String> uploadMultipart(String path, String token, String fileName, String content) {
        return uploadMultipart(path, token, fileName, content.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    private static MediaType contentTypeOf(String fileName) {
        String n = fileName.toLowerCase();
        if (n.endsWith(".csv")) return new MediaType("text", "csv", StandardCharsets.UTF_8);
        if (n.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (n.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    // ------------------------------------------------------------------ auth & users

    protected ResponseEntity<String> loginResponse(String username, String password) {
        return post("/auth/login", null, Map.of("username", username, "password", password));
    }

    protected JsonNode loginJson(String username, String password) {
        return expect(loginResponse(username, password), HttpStatus.OK);
    }

    protected String login(String username, String password) {
        return loginJson(username, password).path("accessToken").asText();
    }

    /** Token admin (cache trong JVM; access token sống 15 phút, đủ cho một lần chạy test). */
    protected String adminToken() {
        return TOKEN_CACHE.computeIfAbsent(ADMIN_USERNAME, k -> login(ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    protected static String uniq(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36) + Integer.toString(ThreadLocalRandom.current().nextInt(1296), 36);
    }

    /** Tài khoản test: id, username, password và token (đăng nhập khi gọi {@link #token()}). */
    public final class TestUser {
        public final UUID id;
        public final String username;
        public final String password;
        public final String email;
        private String token;

        TestUser(UUID id, String username, String password, String email) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.email = email;
        }

        public String token() {
            if (token == null) token = login(username, password);
            return token;
        }
    }

    /** Body ExpertRequest tối thiểu (có thể sửa thêm field trước khi gửi). */
    protected Map<String, Object> expertBody(String fullName, String employmentType, UUID userId) {
        Map<String, Object> b = new HashMap<>();
        b.put("fullName", fullName);
        b.put("expertType", "AUDITOR");
        b.put("employmentType", employmentType);
        b.put("email", uniq("e") + "@expert.local");
        b.put("phone", "0900000000");
        if (userId != null) b.put("userId", userId.toString());
        return b;
    }

    /** Tạo chuyên gia qua API (token cần EXPERT_CREATE, VD role CERTIFICATION_MANAGER). */
    protected JsonNode createExpert(String token, String fullName, String employmentType, UUID userId) {
        return postJson("/experts", token, expertBody(fullName, employmentType, userId), HttpStatus.CREATED);
    }

    /** Tạo user qua API /users (bằng admin) với các role cho trước. */
    protected TestUser createUserWithRoles(String... roleCodes) {
        String username = uniq("u");
        String email = username + "@test.local";
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("fullName", "Test " + username);
        body.put("password", DEFAULT_PASSWORD);
        body.put("roleCodes", List.of(roleCodes));
        JsonNode created = postJson("/users", adminToken(), body, HttpStatus.CREATED);
        return new TestUser(UUID.fromString(created.path("id").asText()), username, DEFAULT_PASSWORD, email);
    }
}
