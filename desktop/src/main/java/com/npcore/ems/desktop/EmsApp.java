package com.npcore.ems.desktop;

import com.npcore.ems.desktop.api.Api;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.LoginView;
import com.npcore.ems.desktop.ui.MainWindow;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** Ứng dụng desktop EMS: đăng nhập → cửa sổ chính. */
public class EmsApp extends Application {

    private Stage stage;
    private final AppConfig config = AppConfig.load();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Dialogs.setOwner(stage);
        stage.setTitle("EMS – Quản lý chuyên gia");
        var icon = getClass().getResourceAsStream("/com/npcore/ems/desktop/icon.png");
        if (icon != null) stage.getIcons().add(new Image(icon));
        showLogin();
        stage.show();
    }

    private void showLogin() {
        LoginView login = new LoginView(config, this::showMain);
        Scene scene = new Scene(login, 980, 640);
        styles(scene);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
    }

    private void showMain(Session session) {
        session.api().client().setOnSessionExpired(() -> Platform.runLater(() -> {
            Dialogs.info("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
            showLogin();
        }));
        MainWindow main = new MainWindow(session, this::logout);
        Scene scene = new Scene(main, Math.max(stage.getWidth(), 1280), Math.max(stage.getHeight(), 800));
        styles(scene);
        stage.setScene(scene);
        if (!stage.isMaximized()) stage.centerOnScreen();
    }

    private void logout(Session session) {
        new Thread(() -> {
            try {
                session.api().logout();
            } catch (RuntimeException ignored) {
                // đăng xuất phía máy chủ thất bại vẫn quay về màn hình đăng nhập
            }
        }, "ems-logout").start();
        showLogin();
    }

    private static void styles(Scene scene) {
        scene.getStylesheets().add(EmsApp.class.getResource("/com/npcore/ems/desktop/app.css").toExternalForm());
    }
}
