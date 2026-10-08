package com.npcore.ems.desktop.ui.expert;

import com.npcore.ems.desktop.api.Dtos.CreatedAccount;
import com.npcore.ems.desktop.api.Dtos.ExpertImportResult;
import com.npcore.ems.desktop.api.Dtos.ImportResult.RowError;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.ImportResultView;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * Chuyển hồ sơ chuyên gia cũ vào hệ thống từ file Excel / CSV; báo lỗi theo từng dòng.
 * Tuỳ chọn tạo luôn tài khoản đăng nhập (vai trò Chuyên gia, mật khẩu tạm, bắt đổi lần đầu) cho dòng có email.
 */
public final class ExpertImportView extends VBox {

    private final ImportResultView result = new ImportResultView();
    private final TableView<RowError> warnings = Tables.table("Không có cảnh báo");
    private final TableView<CreatedAccount> accounts = Tables.table("Không tạo tài khoản nào");
    private final Label warningTitle = Ui.hint("");
    private final Label accountTitle = new Label("Tài khoản đã tạo");
    private final Button saveAccounts = Ui.primary("Lưu danh sách tài khoản (CSV)…", this::saveAccounts);
    private final VBox accountBox;

    public ExpertImportView(Session session) {
        super(12);
        setPadding(new Insets(16));
        CheckBox createAccounts = new CheckBox("Tạo tài khoản đăng nhập cho chuyên gia (dòng có email)");
        createAccounts.setSelected(true);

        warnings.getColumns().addAll(List.of(Tables.col("Dòng", RowError::row, 70),
                Tables.col("Cảnh báo", RowError::message, 600)));
        warnings.setPrefHeight(140);
        accounts.getColumns().addAll(List.of(
                Tables.col("Dòng", CreatedAccount::row, 60),
                Tables.col("Mã CG", CreatedAccount::expertCode, 100),
                Tables.col("Họ và tên", CreatedAccount::fullName, 200),
                Tables.col("Tên đăng nhập", CreatedAccount::username, 160),
                Tables.col("Email", CreatedAccount::email, 200),
                Tables.col("Mật khẩu tạm", CreatedAccount::tempPassword, 130)));
        accountTitle.getStyleClass().add("cell-title");
        accountBox = new VBox(8, accountTitle,
                Ui.hint("Mật khẩu tạm chỉ hiện MỘT lần – hãy lưu danh sách và gửi riêng cho từng chuyên gia. "
                        + "Lần đăng nhập đầu, chuyên gia bắt buộc đổi mật khẩu."),
                Ui.toolbar(saveAccounts), accounts, warningTitle, warnings);
        VBox.setVgrow(accounts, Priority.ALWAYS);
        accountBox.setVisible(false);
        accountBox.setManaged(false);
        VBox.setVgrow(result, Priority.SOMETIMES);
        VBox.setVgrow(accountBox, Priority.ALWAYS);

        getChildren().addAll(
                Ui.title("Import hồ sơ chuyên gia"),
                Ui.hint("File .xlsx hoặc .csv (UTF-8), dòng đầu là tiêu đề cột: expert_code, full_name, date_of_birth, "
                        + "gender, id_number, address, phone, email, expert_type, employment_type, department_code, "
                        + "position, joined_date, username. Để trống expert_code thì hệ thống tự cấp mã; để trống "
                        + "username thì lấy phần trước @ của email. Dòng hợp lệ được nhập, dòng lỗi được liệt kê bên dưới."),
                createAccounts,
                Ui.toolbar(
                        Ui.primary("Chọn file và import…", () -> {
                            FileChooser fc = new FileChooser();
                            fc.setTitle("Chọn file hồ sơ chuyên gia");
                            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel / CSV", "*.xlsx", "*.csv"));
                            File f = fc.showOpenDialog(getScene().getWindow());
                            boolean withAccounts = createAccounts.isSelected();
                            if (f != null) {
                                Async.run(this, () -> session.api().importExperts(f.toPath(), withAccounts), this::show);
                            }
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
                result, accountBox);
    }

    private void show(ExpertImportResult r) {
        result.show(r.basic());
        List<CreatedAccount> acc = r.accounts() == null ? List.of() : r.accounts();
        List<RowError> warn = r.warnings() == null ? List.of() : r.warnings();
        boolean any = !acc.isEmpty() || !warn.isEmpty();
        accountBox.setVisible(any);
        accountBox.setManaged(any);
        accountTitle.setText("Tài khoản đã tạo: " + acc.size());
        accounts.getItems().setAll(acc);
        saveAccounts.setDisable(acc.isEmpty());
        warningTitle.setText(warn.isEmpty() ? "" : "Hồ sơ vẫn được nhập nhưng chưa tạo tài khoản (" + warn.size()
                + ") – gắn tài khoản bằng tay trong hồ sơ nếu cần:");
        warnings.getItems().setAll(warn);
        warnings.setVisible(!warn.isEmpty());
        warnings.setManaged(!warn.isEmpty());
        if (!acc.isEmpty()) {
            Dialogs.info("Đã tạo " + acc.size() + " tài khoản. Hãy bấm \"Lưu danh sách tài khoản\" ngay – "
                    + "mật khẩu tạm sẽ không hiện lại.");
        }
    }

    private void saveAccounts() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Lưu danh sách tài khoản chuyên gia");
        fc.setInitialFileName("tai-khoan-chuyen-gia-" + LocalDate.now() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (Excel)", "*.csv"));
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        String body = Stream.concat(
                        Stream.of(List.of("Mã CG", "Họ và tên", "Tên đăng nhập", "Email", "Mật khẩu tạm")),
                        accounts.getItems().stream().map(a -> List.of(a.expertCode(), a.fullName(), a.username(),
                                a.email(), a.tempPassword())))
                .map(ExpertImportView::csv).collect(Collectors.joining("\r\n"));
        try {
            Files.writeString(file.toPath(), "﻿" + body + "\r\n", StandardCharsets.UTF_8); // BOM cho Excel
            Dialogs.info("Đã lưu " + accounts.getItems().size() + " tài khoản ra\n" + file.getAbsolutePath()
                    + "\n\nFile chứa mật khẩu – gửi riêng từng người rồi xoá file.");
        } catch (IOException ex) {
            Dialogs.info("Không ghi được file: " + ex.getMessage());
        }
    }

    private static String csv(List<String> cells) {
        return cells.stream().map(v -> v == null ? "" : v)
                .map(v -> v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v)
                .collect(Collectors.joining(","));
    }
}
