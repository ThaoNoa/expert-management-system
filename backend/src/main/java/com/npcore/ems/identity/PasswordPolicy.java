package com.npcore.ems.identity;

import com.npcore.ems.shared.settings.SettingsService;
import com.npcore.ems.shared.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** NFR-S-03: tối thiểu N ký tự (cấu hình), có chữ, số và ký tự đặc biệt. */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    private final SettingsService settings;

    public void check(String password) {
        int min = settings.getInt("security.passwordMinLength", 8);
        if (password == null || password.length() < min
                || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw ApiException.badRequest("Mật khẩu phải dài tối thiểu " + min
                    + " ký tự, gồm chữ, số và ký tự đặc biệt");
        }
    }
}
