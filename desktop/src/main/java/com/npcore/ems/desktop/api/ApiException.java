package com.npcore.ems.desktop.api;

import java.util.List;
import java.util.Map;

/** Lỗi trả về từ máy chủ (ProblemDetail: status, code, detail, errors[field,message]) hoặc lỗi kết nối. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ApiException(int status, String code, String detail, Map<String, String> fieldErrors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : fieldErrors;
    }

    public static ApiException connection(String message) {
        return new ApiException(0, "CONNECTION", message, Map.of());
    }

    public int status() { return status; }

    public String code() { return code; }

    public Map<String, String> fieldErrors() { return fieldErrors; }

    /** Thông báo hiển thị cho người dùng, kèm lỗi theo trường nếu có. */
    public String userMessage() {
        if (fieldErrors.isEmpty()) return getMessage();
        StringBuilder sb = new StringBuilder(getMessage() == null ? "" : getMessage());
        List<String> lines = fieldErrors.entrySet().stream().map(e -> "• " + e.getKey() + ": " + e.getValue()).toList();
        for (String l : lines) sb.append('\n').append(l);
        return sb.toString();
    }
}
