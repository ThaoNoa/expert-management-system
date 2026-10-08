package com.npcore.ems.desktop.ui.fx;

import com.npcore.ems.desktop.api.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Form nhập liệu dựng bằng code: khai báo trường → lưới 2 cột nhãn/ô nhập.
 * Dùng với {@link #showDialog} để mở hộp thoại, kiểm tra bắt buộc, gọi máy chủ và hiển thị lỗi theo trường.
 */
public final class Form {

    /** Lựa chọn trong ComboBox: giá trị gửi lên máy chủ + nhãn hiển thị. */
    public record Option<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record Field(String key, String label, Control control, boolean required) {}

    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final GridPane grid = new GridPane();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(12);
    private int row;

    public Form() {
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints(150);
        ColumnConstraints c2 = new ColumnConstraints(280, 360, Double.MAX_VALUE);
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c1, c2);
        errorLabel.getStyleClass().add("form-error");
        errorLabel.setWrapText(true);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        root.getChildren().addAll(errorLabel, grid);
    }

    // ---------------------------------------------------------------- khai báo trường

    public Form text(String key, String label, boolean required) {
        return add(key, label, new TextField(), required);
    }

    public Form password(String key, String label, boolean required) {
        return add(key, label, new PasswordField(), required);
    }

    public Form area(String key, String label, boolean required) {
        TextArea a = new TextArea();
        a.setPrefRowCount(3);
        a.setWrapText(true);
        return add(key, label, a, required);
    }

    public Form date(String key, String label, boolean required) {
        DatePicker p = new DatePicker();
        p.setConverter(dateConverter());
        p.setPromptText("dd/MM/yyyy");
        p.setMaxWidth(Double.MAX_VALUE);
        return add(key, label, p, required);
    }

    public Form check(String key, String label) {
        return add(key, label, new CheckBox(), false);
    }

    public <T> Form choice(String key, String label, List<Option<T>> options, boolean required) {
        ComboBox<Option<T>> box = new ComboBox<>();
        box.getItems().setAll(options);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setVisibleRowCount(12);
        return add(key, label, box, required);
    }

    /** Chọn nhiều (danh sách có checkbox-like multi select, giữ Ctrl để chọn nhiều). */
    public <T> Form multi(String key, String label, List<Option<T>> options) {
        javafx.scene.control.ListView<Option<T>> list = new javafx.scene.control.ListView<>();
        list.getItems().setAll(options);
        list.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        list.setPrefHeight(Math.min(160, 26 * Math.max(3, options.size()) + 4));
        return add(key, label, list, false);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> values(String key) {
        javafx.scene.control.ListView<Option<T>> list = (javafx.scene.control.ListView<Option<T>>) control(key);
        return list.getSelectionModel().getSelectedItems().stream().map(Option::value).toList();
    }

    @SuppressWarnings("unchecked")
    public <T> Form selectValues(String key, java.util.Collection<T> values) {
        javafx.scene.control.ListView<Option<T>> list = (javafx.scene.control.ListView<Option<T>>) control(key);
        list.getSelectionModel().clearSelection();
        for (int i = 0; i < list.getItems().size(); i++) {
            if (values != null && values.contains(list.getItems().get(i).value())) list.getSelectionModel().select(i);
        }
        return this;
    }

    /** Lựa chọn từ một danh sách mã (mã → nhãn bằng Fmt.label). */
    public Form codes(String key, String label, List<String> codes, boolean required) {
        List<Option<String>> opts = new ArrayList<>();
        if (!required) opts.add(new Option<>(null, "—"));
        codes.forEach(c -> opts.add(new Option<>(c, Fmt.label(c))));
        return choice(key, label, opts, required);
    }

    private Form add(String key, String label, Control control, boolean required) {
        Label l = new Label(label + (required ? " *" : ""));
        l.getStyleClass().add("form-label");
        l.setWrapText(true);
        if (control instanceof TextInputControl || control instanceof ComboBox || control instanceof DatePicker) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
        grid.add(l, 0, row);
        grid.add(control, 1, row);
        row++;
        fields.put(key, new Field(key, label, control, required));
        return this;
    }

    /** Thêm dòng chú thích dưới form. */
    public Form note(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint");
        l.setWrapText(true);
        grid.add(l, 0, row++, 2, 1);
        return this;
    }

    public Form disable(String key, boolean disabled) {
        control(key).setDisable(disabled);
        return this;
    }

    // ---------------------------------------------------------------- đọc / ghi giá trị

    public Control control(String key) {
        Field f = fields.get(key);
        if (f == null) throw new IllegalArgumentException("Không có trường " + key);
        return f.control();
    }

    public Form set(String key, Object value) {
        Control c = control(key);
        if (c instanceof TextInputControl t) t.setText(value == null ? "" : Fmt.text(value));
        else if (c instanceof DatePicker d) d.setValue((LocalDate) value);
        else if (c instanceof CheckBox b) b.setSelected(Boolean.TRUE.equals(value));
        else if (c instanceof ComboBox<?> box) selectOption(box, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    private static <T> void selectOption(ComboBox<?> box, Object value) {
        ComboBox<Option<T>> b = (ComboBox<Option<T>>) box;
        b.getItems().stream().filter(o -> Objects.equals(o.value(), value)).findFirst()
                .ifPresentOrElse(o -> b.getSelectionModel().select(o), () -> b.getSelectionModel().clearSelection());
    }

    public String str(String key) {
        Control c = control(key);
        if (c instanceof TextInputControl t) {
            String v = t.getText();
            return v == null || v.isBlank() ? null : v.trim();
        }
        Object v = value(key);
        return v == null ? null : v.toString();
    }

    public LocalDate date(String key) {
        DatePicker p = (DatePicker) control(key);
        commitDate(p);
        return p.getValue();
    }

    public boolean bool(String key) {
        return ((CheckBox) control(key)).isSelected();
    }

    public BigDecimal decimal(String key) {
        String s = str(key);
        if (s == null) return null;
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ApiException(400, "VALIDATION_ERROR", "Giá trị số không hợp lệ", Map.of(fields.get(key).label(), s));
        }
    }

    public Integer integer(String key) {
        BigDecimal d = decimal(key);
        return d == null ? null : d.intValueExact();
    }

    @SuppressWarnings("unchecked")
    public <T> T value(String key) {
        Control c = control(key);
        if (c instanceof ComboBox<?> box) {
            Object sel = box.getSelectionModel().getSelectedItem();
            return sel == null ? null : ((Option<T>) sel).value();
        }
        throw new IllegalArgumentException(key + " không phải ComboBox");
    }

    // ---------------------------------------------------------------- hiển thị

    /** Nút gốc của form (luôn cùng một đối tượng; có thể chèn thêm nội dung vào). */
    public VBox node() {
        return root;
    }

    /** Kiểm tra các trường bắt buộc; trả về thông báo lỗi hoặc null. */
    public String validateRequired() {
        clearErrors();
        List<String> missing = new ArrayList<>();
        for (Field f : fields.values()) {
            if (!f.required()) continue;
            boolean empty = switch (f.control()) {
                case TextInputControl t -> t.getText() == null || t.getText().isBlank();
                case DatePicker d -> {
                    commitDate(d);
                    yield d.getValue() == null;
                }
                case ComboBox<?> b -> b.getSelectionModel().getSelectedItem() == null;
                default -> false;
            };
            if (empty) {
                missing.add(f.label());
                f.control().getStyleClass().add("field-error");
            }
        }
        return missing.isEmpty() ? null : "Chưa nhập: " + String.join(", ", missing);
    }

    public void showError(ApiException e) {
        e.fieldErrors().keySet().forEach(k -> {
            Field f = fields.get(k);
            if (f != null) f.control().getStyleClass().add("field-error");
        });
        showMessage(e.userMessage());
    }

    public void showMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void clearErrors() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        fields.values().forEach(f -> f.control().getStyleClass().remove("field-error"));
    }

    /**
     * Mở hộp thoại chứa form. Khi bấm Lưu: kiểm tra bắt buộc → chạy submit ngoài UI thread →
     * thành công thì đóng và gọi onDone; lỗi thì giữ hộp thoại, hiện lỗi theo trường.
     */
    private double dialogWidth = -1, dialogHeight = -1;

    /** Đặt kích thước vùng nội dung hộp thoại (cho form có bảng/ma trận lớn). */
    public Form size(double width, double height) {
        this.dialogWidth = width;
        this.dialogHeight = height;
        return this;
    }

    public <T> void showDialog(String title, String okText, Callable<T> submit, Consumer<T> onDone) {
        showDialog(title, okText, submit, onDone, null);
    }

    /** Như trên; onCancel chạy khi hộp thoại đóng mà chưa lưu thành công (Huỷ / nút X). */
    public <T> void showDialog(String title, String okText, Callable<T> submit, Consumer<T> onDone, Runnable onCancel) {
        Dialog<ButtonType> d = new Dialog<>();
        Dialogs.init(d, title);
        ScrollPane sp = new ScrollPane(node());
        sp.setFitToWidth(true);
        sp.setPrefViewportWidth(dialogWidth > 0 ? dialogWidth : 560);
        sp.setPrefViewportHeight(dialogHeight > 0 ? dialogHeight : Math.min(560, 60 + row * 44));
        d.setResizable(true);
        sp.getStyleClass().add("form-scroll");
        d.getDialogPane().setContent(sp);
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Huỷ", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button okButton = (Button) d.getDialogPane().lookupButton(ok);
        okButton.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            String missing = validateRequired();
            if (missing != null) {
                showMessage(missing);
                return;
            }
            Async.run(d.getDialogPane(), submit, result -> {
                d.setResult(ok);
                d.close();
                onDone.accept(result);
            }, this::showError);
        });
        if (onCancel != null) d.setOnHidden(e -> {
            if (d.getResult() != ok) onCancel.run();
        });
        d.show();
    }

    private static void commitDate(DatePicker p) {
        String text = p.getEditor().getText();
        if (text == null || text.isBlank()) {
            p.setValue(null);
            return;
        }
        try {
            p.setValue(p.getConverter().fromString(text));
        } catch (RuntimeException ignored) {
            // giữ giá trị cũ
        }
    }

    public static StringConverter<LocalDate> dateConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(LocalDate d) {
                return d == null ? "" : Fmt.DATE.format(d);
            }

            @Override
            public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                String t = s.trim();
                return t.contains("/") ? LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"))
                        : LocalDate.parse(t);
            }
        };
    }
}
