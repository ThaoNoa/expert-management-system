package com.npcore.ems.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Cấu hình cục bộ của máy trạm: ~/.ems/desktop.properties (địa chỉ máy chủ, tên đăng nhập gần nhất). */
public final class AppConfig {

    private static final Path FILE = Path.of(System.getProperty("user.home"), ".ems", "desktop.properties");
    private final Properties props = new Properties();

    private AppConfig() {}

    public static AppConfig load() {
        AppConfig c = new AppConfig();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                c.props.load(in);
            } catch (IOException ignored) {
                // dùng mặc định
            }
        }
        return c;
    }

    public String serverUrl() {
        String env = System.getenv("EMS_SERVER_URL");
        if (env != null && !env.isBlank()) return env;
        return props.getProperty("server.url", "http://localhost:8080");
    }

    public String lastUsername() {
        return props.getProperty("last.username", "");
    }

    public void save(String serverUrl, String username) {
        props.setProperty("server.url", serverUrl.trim());
        props.setProperty("last.username", username.trim());
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "EMS desktop");
            }
        } catch (IOException ignored) {
            // không lưu được cấu hình thì vẫn cho dùng tiếp
        }
    }
}
