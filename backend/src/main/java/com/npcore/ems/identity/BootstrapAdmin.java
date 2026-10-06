package com.npcore.ems.identity;

import com.npcore.ems.config.EmsProperties;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lần đầu khởi động (chưa có user nào): tạo tài khoản SUPER_ADMIN từ biến môi trường. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final EmsProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        var b = props.bootstrap();
        User admin = new User();
        admin.setUsername(b.adminUsername());
        admin.setEmail(b.adminEmail());
        admin.setFullName("Quản trị hệ thống");
        admin.setPasswordHash(passwordEncoder.encode(b.adminPassword()));
        admin.setPasswordChangedAt(OffsetDateTime.now());
        admin.setRoles(new HashSet<>(roles.findByCodeIn(List.of("SUPER_ADMIN"))));
        users.save(admin);
        log.warn("Đã tạo tài khoản quản trị ban đầu '{}'. Hãy đổi mật khẩu ngay sau khi đăng nhập.", b.adminUsername());
    }
}
