package com.npcore.ems.desktop.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiClientTest {

    @Test
    void fileNamePrefersRfc5987Utf8Name() {
        String h = "attachment; filename=\"bang-cap.pdf\"; filename*=UTF-8''B%E1%BA%B1ng%20c%E1%BA%A5p.pdf";
        assertThat(ApiClient.fileName(Optional.of(h))).contains("Bằng cấp.pdf");
    }

    @Test
    void fileNameFallsBackToPlainName() {
        assertThat(ApiClient.fileName(Optional.of("attachment; filename=\"cv.docx\""))).contains("cv.docx");
        assertThat(ApiClient.fileName(Optional.empty())).isEmpty();
    }

    @Test
    void queryEncodesValuesAndSkipsBlank() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("q", "Nguyễn Văn A");
        p.put("status", null);
        p.put("type", " ");
        p.put("page", 2);
        assertThat(ApiClient.query(p)).isEqualTo("?q=Nguy%E1%BB%85n+V%C4%83n+A&page=2");
        assertThat(ApiClient.query(Map.of())).isEmpty();
    }

    @Test
    void userMessageListsFieldErrors() {
        ApiException e = new ApiException(400, "VALIDATION", "Dữ liệu không hợp lệ", Map.of("email", "sai định dạng"));
        assertThat(e.userMessage()).isEqualTo("Dữ liệu không hợp lệ\n• email: sai định dạng");
    }
}
