package com.npcore.ems.desktop.ui.expert;

import com.npcore.ems.desktop.api.Dtos.ExpertSummary;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.Navigator;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.PagedTable;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Danh sách chuyên gia: lọc + phân trang phía máy chủ (FR-5.1), nhấp đúp để mở hồ sơ. */
public final class ExpertListView extends VBox {

    private final TextField q = new TextField();
    private final ComboBox<Option<String>> type = combo("Loại chuyên gia", "AUDITOR", "TECHNICAL_EXPERT");
    private final ComboBox<Option<String>> employment = combo("Hình thức", "FULLTIME", "PARTTIME");
    private final ComboBox<Option<String>> status = combo("Trạng thái", "DRAFT", "SUBMITTED", "ACTIVE", "SUSPENDED", "INACTIVE");

    public ExpertListView(Session session, Navigator nav) {
        this(session, nav, null, "Danh sách chuyên gia");
    }

    /** presetStatus != null: màn hình hàng đợi (VD "Chờ phê duyệt" của GĐCN). */
    public ExpertListView(Session session, Navigator nav, String presetStatus, String title) {
        super(12);
        if (presetStatus != null) {
            status.getItems().stream().filter(o -> presetStatus.equals(o.value())).findFirst()
                    .ifPresent(o -> status.getSelectionModel().select(o));
        }
        setPadding(new Insets(16));
        TableView<ExpertSummary> table = Tables.table("Không có chuyên gia phù hợp");
        table.getColumns().addAll(List.of(
                Tables.col("Mã", ExpertSummary::expertCode, 90),
                Tables.col("Họ và tên", ExpertSummary::fullName, 220),
                Tables.col("Loại", e -> Fmt.label(e.expertType()), 160),
                Tables.col("Hình thức", e -> Fmt.label(e.employmentType()), 120),
                Tables.col("Phòng ban", ExpertSummary::departmentName, 140),
                Tables.col("Email", ExpertSummary::email, 200),
                Tables.col("Điện thoại", ExpertSummary::phone, 110),
                Tables.status("Trạng thái", ExpertSummary::status, 150),
                Tables.col("Dừng đến hết", e -> Fmt.date(e.suspendedUntil()), 100)));
        PagedTable<ExpertSummary> paged = new PagedTable<>(table, (page, size) -> session.api().experts(
                blank(q.getText()), value(type), value(employment), value(status), page, size, "code,asc"));
        Tables.onOpen(table, e -> nav.open("expert:" + e.id(), e.expertCode() + " · " + e.fullName(),
                () -> new ExpertDetailView(session, nav, e.id())));

        q.setPromptText("Tìm theo tên (không dấu cũng được), mã hoặc email");
        q.setPrefWidth(260);
        q.setOnAction(e -> paged.reload());
        for (ComboBox<Option<String>> c : List.of(type, employment, status)) c.setOnAction(e -> paged.reload());

        var toolbar = Ui.toolbar(q, type, employment, status, Ui.button("Tìm", paged::reload), Ui.spacer());
        if (session.has("EXPERT_CREATE")) {
            toolbar.getChildren().add(Ui.primary("+ Thêm chuyên gia", () -> ExpertForm.open(session, this, null, created -> {
                paged.refresh();
                nav.open("expert:" + created.id(), created.expertCode() + " · " + created.fullName(),
                        () -> new ExpertDetailView(session, nav, created.id()));
            })));
        }
        VBox.setVgrow(paged, Priority.ALWAYS);
        getChildren().addAll(Ui.title(title), toolbar, paged);
        paged.reload();
    }

    private static ComboBox<Option<String>> combo(String prompt, String... codes) {
        List<Option<String>> opts = new ArrayList<>();
        opts.add(new Option<>(null, "Tất cả – " + prompt.toLowerCase()));
        for (String c : codes) opts.add(new Option<>(c, Fmt.label(c)));
        ComboBox<Option<String>> box = new ComboBox<>();
        box.getItems().setAll(opts);
        box.setPromptText(prompt);
        return box;
    }

    private static String value(ComboBox<Option<String>> box) {
        Option<String> o = box.getValue();
        return o == null ? null : o.value();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
