package com.npcore.ems.desktop.ui.fx;

import com.npcore.ems.desktop.api.ApiException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;

/**
 * Chạy lời gọi máy chủ ngoài JavaFX thread rồi trả kết quả về UI thread.
 * Trong lúc chạy, node "busy" (nếu có) bị vô hiệu hoá và con trỏ chuột chuyển sang chờ.
 */
public final class Async {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ems-api");
        t.setDaemon(true);
        return t;
    });

    private Async() {}

    public static <T> void run(Node busy, Callable<T> work, Consumer<T> onSuccess) {
        run(busy, work, onSuccess, Dialogs::error);
    }

    public static <T> void run(Node busy, Callable<T> work, Consumer<T> onSuccess, Consumer<ApiException> onError) {
        setBusy(busy, true);
        POOL.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> {
                    setBusy(busy, false);
                    onSuccess.accept(result);
                });
            } catch (ApiException e) {
                Platform.runLater(() -> {
                    setBusy(busy, false);
                    onError.accept(e);
                });
            } catch (Exception e) {
                ApiException wrapped = new ApiException(0, "CLIENT_ERROR", "Lỗi: " + e.getMessage(), null);
                Platform.runLater(() -> {
                    setBusy(busy, false);
                    onError.accept(wrapped);
                });
            }
        });
    }

    /** Lời gọi không có kết quả. */
    public static void exec(Node busy, ThrowingRunnable work, Runnable onSuccess) {
        run(busy, () -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> onSuccess.run());
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void setBusy(Node node, boolean busy) {
        if (node == null) return;
        node.setDisable(busy);
        if (node.getScene() != null) {
            node.getScene().setCursor(busy ? javafx.scene.Cursor.WAIT : javafx.scene.Cursor.DEFAULT);
        }
    }
}
