package com.npcore.ems.desktop.ui;

import com.npcore.ems.desktop.AppConfig;
import com.npcore.ems.desktop.api.Api;
import com.npcore.ems.desktop.api.ApiClient;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Màn hình đăng nhập: tên đăng nhập, mật khẩu, địa chỉ máy chủ (lưu cho lần sau). */
public final class LoginView extends StackPane {

    public LoginView(AppConfig config, Consumer<Session> onLoggedIn) {
        getStyleClass().add("login-bg");

        Label title = new Label("EMS");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Phần mềm quản lý chuyên gia đánh giá");
        subtitle.getStyleClass().add("hint");

        TextField username = new TextField(config.lastUsername());
        username.setPromptText("Tên đăng nhập");
        PasswordField password = new PasswordField();
        password.setPromptText("Mật khẩu");
        TextField server = new TextField(config.serverUrl());
        server.setPromptText("http://may-chu:8080");
        TitledPane serverPane = new TitledPane("Máy chủ", new VBox(6, new Label("Địa chỉ máy chủ EMS"), server));
        serverPane.setExpanded(false);
        serverPane.getStyleClass().add("server-pane");

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        Button login = new Button("Đăng nhập");
        login.getStyleClass().add("primary");
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(12, title, subtitle, new Label("Tên đăng nhập"), username, new Label("Mật khẩu"),
                password, error, login, serverPane);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(32));
        card.setMaxWidth(380);
        card.setMaxHeight(VBox.USE_PREF_SIZE);
        setAlignment(Pos.CENTER);
        getChildren().add(card);

        login.setOnAction(e -> {
            if (username.getText().isBlank() || password.getText().isEmpty()) {
                show(error, "Nhập tên đăng nhập và mật khẩu");
                return;
            }
            error.setVisible(false);
            error.setManaged(false);
            String url = server.getText().isBlank() ? "http://localhost:8080" : server.getText().trim();
            Api api = new Api(new ApiClient(url));
            Async.run(card, () -> api.login(username.getText().trim(), password.getText()), token -> {
                config.save(url, username.getText());
                onLoggedIn.accept(new Session(api, token.user()));
            }, ex -> {
                show(error, ex.userMessage());
                password.clear();
                password.requestFocus();
            });
        });
        javafx.application.Platform.runLater(() -> (username.getText().isBlank() ? username : password).requestFocus());
    }

    private static void show(Label l, String text) {
        l.setText(text);
        l.setVisible(true);
        l.setManaged(true);
    }
}
