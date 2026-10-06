package com.npcore.ems.shared.web;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Mọi lỗi trả về dạng RFC 7807 + "code" (+ "errors" theo field). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraint(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleUnreadable(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Yêu cầu không đọc được: " + ex.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUpload(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File vượt quá dung lượng cho phép", null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class})
    ProblemDetail handleDenied(Exception ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền thực hiện thao tác này", null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimistic(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", "Dữ liệu đã được người khác thay đổi, vui lòng tải lại", null);
    }

    /** Lỗi từ ràng buộc / trigger DB (unique, check, state machine, append-only...). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && (msg.contains("duplicate key") || msg.contains("unique"))) {
            return problem(HttpStatus.CONFLICT, "DUPLICATE", "Dữ liệu đã tồn tại", null);
        }
        if (msg != null && msg.contains("Illegal")) {
            return problem(HttpStatus.CONFLICT, "ILLEGAL_TRANSITION", firstLine(msg), null);
        }
        if (msg != null && msg.contains("foreign key")) {
            return problem(HttpStatus.CONFLICT, "CONFLICT", "Dữ liệu đang được tham chiếu hoặc tham chiếu không tồn tại", null);
        }
        log.warn("Data integrity violation: {}", msg);
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", firstLine(msg), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy", null);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleOther(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống", null);
    }

    private static String firstLine(String s) {
        if (s == null) return "Vi phạm ràng buộc dữ liệu";
        int i = s.indexOf('\n');
        return (i > 0 ? s.substring(0, i) : s).replace("ERROR: ", "");
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail, List<Map<String, String>> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        if (errors != null) pd.setProperty("errors", errors);
        return pd;
    }
}
