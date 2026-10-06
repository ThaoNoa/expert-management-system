package com.npcore.ems.desktop.ui.expert;

import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.ImportResultView;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.io.File;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** Chuyển hồ sơ chuyên gia cũ vào hệ thống từ file Excel / CSV; báo lỗi theo từng dòng. */
public final class ExpertImportView extends VBox {

    public ExpertImportView(Session session) {
        super(12);
        setPadding(new Insets(16));
        ImportResultView result = new ImportResultView();
        VBox.setVgrow(result, Priority.ALWAYS);
        getChildren().addAll(
                Ui.title("Import hồ sơ chuyên gia"),
                Ui.hint("File .xlsx hoặc .csv (UTF-8), dòng đầu là tiêu đề cột: expert_code, full_name, date_of_birth, "
                        + "gender, id_number, address, phone, email, expert_type, employment_type, department_code, "
                        + "position, joined_date. Để trống expert_code thì hệ thống tự cấp mã. Dòng hợp lệ được nhập, "
                        + "dòng lỗi được liệt kê bên dưới."),
                Ui.toolbar(
                        Ui.primary("Chọn file và import…", () -> {
                            FileChooser fc = new FileChooser();
                            fc.setTitle("Chọn file hồ sơ chuyên gia");
                            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel / CSV", "*.xlsx", "*.csv"));
                            File f = fc.showOpenDialog(getScene().getWindow());
                            if (f != null) Async.run(this, () -> session.api().importExperts(f.toPath()), result::show);
                        }),
                        Ui.button("Tải file mẫu…", () -> {
                            DirectoryChooser dc = new DirectoryChooser();
                            dc.setTitle("Lưu file mẫu vào thư mục");
                            File dir = dc.showDialog(getScene().getWindow());
                            if (dir != null) {
                                Async.run(this, () -> session.api().downloadExpertTemplate(dir.toPath()),
                                        (Path p) -> Dialogs.info("Đã lưu: " + p));
                            }
                        })),
                result);
    }
}
