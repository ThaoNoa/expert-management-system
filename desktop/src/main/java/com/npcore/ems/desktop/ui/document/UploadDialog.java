package com.npcore.ems.desktop.ui.document;

import com.npcore.ems.desktop.api.Dtos.ExpertSummary;
import com.npcore.ems.desktop.api.Dtos.UploadResult;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Lookups;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

/** Hộp thoại upload tài liệu (chống trùng SHA-256 ở máy chủ: file đã có thì dùng lại). */
public final class UploadDialog {

    private UploadDialog() {}

    /**
     * @param fixedOwner chuyên gia sở hữu (null = cho chọn); experts = danh sách để chọn khi không cố định
     */
    public static void open(Session session, Lookups lookups, UUID fixedOwner, List<ExpertSummary> experts,
                            Consumer<UploadResult> onDone) {
        open(session, lookups, fixedOwner, null, experts, onDone);
    }

    /** ownerLabel: tên chuyên gia hiển thị trên tiêu đề hộp thoại (khi fixedOwner != null). */
    public static void open(Session session, Lookups lookups, UUID fixedOwner, String ownerLabel,
                            List<ExpertSummary> experts, Consumer<UploadResult> onDone) {
        Path[] chosen = new Path[1];
        Form f = new Form();
        f.choice("type", "Loại tài liệu", lookups.documentTypeOptions(), true)
                .text("title", "Tiêu đề", false);
        if (fixedOwner == null && !experts.isEmpty()) {
            f.choice("owner", "Thuộc chuyên gia", Lookups.withNone(experts.stream()
                    .map(e -> new Option<>(e.id(), e.expertCode() + " – " + e.fullName())).toList()), false);
        }
        f.date("issued", "Ngày cấp", false).date("expiry", "Ngày hết hạn", false)
                .note("Chứng chỉ, cam kết bảo mật, cam kết khách quan bắt buộc có ngày hết hạn. "
                        + "Nếu file đã có trong hệ thống, tài liệu sẵn có sẽ được dùng lại (không lưu trùng).");

        // Ô chọn file đặt trên cùng form
        TextField path = new TextField();
        path.setEditable(false);
        path.setPromptText("Chưa chọn file");
        Button browse = new Button("Chọn file…");
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Chọn tài liệu");
            File file = fc.showOpenDialog(browse.getScene().getWindow());
            if (file != null) {
                chosen[0] = file.toPath();
                path.setText(file.getAbsolutePath());
            }
        });
        HBox.setHgrow(path, Priority.ALWAYS);
        HBox fileRow = new HBox(8, new Label("File *"), path, browse);
        f.node().getChildren().add(1, fileRow);

        f.showDialog(ownerLabel == null ? "Tải tài liệu lên" : "Tải tài liệu lên – " + ownerLabel, "Tải lên", () -> {
            if (chosen[0] == null) {
                throw new com.npcore.ems.desktop.api.ApiException(400, "VALIDATION_ERROR", "Chưa chọn file", null);
            }
            String type = f.value("type");
            if (lookups.requiresExpiry(type) && f.date("expiry") == null) {
                throw new com.npcore.ems.desktop.api.ApiException(400, "VALIDATION_ERROR",
                        "Loại tài liệu này bắt buộc nhập ngày hết hạn", null);
            }
            UUID owner = fixedOwner != null ? fixedOwner : (!experts.isEmpty() ? f.value("owner") : null);
            return session.api().uploadDocument(chosen[0], type, f.str("title"), owner, f.date("issued"),
                    f.date("expiry"), null, null);
        }, result -> {
            if (result.duplicate()) Dialogs.info("File đã tồn tại trong hệ thống – đã dùng lại tài liệu có sẵn: "
                    + result.document().title());
            onDone.accept(result);
        });
    }
}
