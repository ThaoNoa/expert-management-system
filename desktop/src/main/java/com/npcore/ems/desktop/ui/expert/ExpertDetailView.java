package com.npcore.ems.desktop.ui.expert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.npcore.ems.desktop.api.Api;
import com.npcore.ems.desktop.api.Dtos.Certificate;
import com.npcore.ems.desktop.api.Dtos.CertificateRequest;
import com.npcore.ems.desktop.api.Dtos.CompetencyDefinition;
import com.npcore.ems.desktop.api.Dtos.EvidenceRequest;
import com.npcore.ems.desktop.api.Dtos.ExpertCompetency;
import com.npcore.ems.desktop.api.Dtos.ExpertCompetencyRequest;
import com.npcore.ems.desktop.api.Dtos.DocumentSummary;
import com.npcore.ems.desktop.api.Dtos.Education;
import com.npcore.ems.desktop.api.Dtos.EducationRequest;
import com.npcore.ems.desktop.api.Dtos.Experience;
import com.npcore.ems.desktop.api.Dtos.ExperienceRequest;
import com.npcore.ems.desktop.api.Dtos.ExpertDetail;
import com.npcore.ems.desktop.api.Dtos.HistoryEntry;
import com.npcore.ems.desktop.api.Dtos.Language;
import com.npcore.ems.desktop.api.Dtos.LanguageRequest;
import com.npcore.ems.desktop.api.Dtos.Training;
import com.npcore.ems.desktop.api.Dtos.TrainingRequest;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.Navigator;
import com.npcore.ems.desktop.ui.document.ExpertDocumentsPane;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Lookups;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Hồ sơ chuyên gia: thông tin chung, học vấn, kinh nghiệm, đào tạo, chứng chỉ, ngôn ngữ, tài liệu, lịch sử.
 * expertId == null → "Hồ sơ của tôi" (GET /experts/me).
 */
public final class ExpertDetailView extends BorderPane {

    private final Session session;
    private final Api api;
    private final UUID requestedId;
    private ExpertDetail expert;
    private Lookups lookups;

    public ExpertDetailView(Session session, Navigator nav, UUID expertId) {
        this.session = session;
        this.api = session.api();
        this.requestedId = expertId;
        setPadding(new Insets(16));
        setCenter(new Label("Đang tải hồ sơ…"));
        load(true);
    }

    private void load(boolean withLookups) {
        Async.run(this, () -> {
            ExpertDetail d = requestedId == null ? api.myExpert() : api.expert(requestedId);
            Lookups l = withLookups || lookups == null ? Lookups.load(session) : lookups;
            return new Object[] {d, l};
        }, r -> {
            expert = (ExpertDetail) r[0];
            lookups = (Lookups) r[1];
            render();
        });
    }

    private void reloadHeader() {
        load(false);
    }

    // ------------------------------------------------------------------ layout

    private void render() {
        setTop(header());
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("Thông tin chung", general()),
                tab("Năng lực", competencies()),
                tab("Học vấn (" + expert.counts().educations() + ")", educations()),
                tab("Kinh nghiệm (" + expert.counts().experiences() + ")", experiences()),
                tab("Đào tạo (" + expert.counts().trainings() + ")", trainings()),
                tab("Chứng chỉ (" + expert.counts().certificates() + ")", certificates()),
                tab("Ngôn ngữ", languages()),
                tab("Tài liệu (" + expert.counts().documents() + ")", documents()),
                tab("Lịch sử", history()));
        BorderPane.setMargin(tabs, new Insets(12, 0, 0, 0));
        setCenter(tabs);
    }

    private static Tab tab(String title, Node content) {
        return new Tab(title, content);
    }

    /** Đã trình: đang chờ thẩm tra hoặc chờ GĐCN phê duyệt → khoá sửa. */
    private boolean locked() {
        return "SUBMITTED".equals(expert.status()) || "REVIEWED".equals(expert.status());
    }

    /** Năng lực (học vấn, kinh nghiệm, code, đào tạo, chứng chỉ, ngoại ngữ) – NV hồ sơ. Khoá khi đã trình. */
    private boolean canEdit() {
        return session.has("EXPERT_COMPETENCY_EDIT") && !locked();
    }

    /** Thông tin chung / nhân sự – Văn phòng, NV hồ sơ. */
    private boolean canEditGeneral() {
        return session.has("EXPERT_EDIT") && !locked();
    }

    /** Chuyên gia tự sửa liên hệ của mình. */
    private boolean canEditContact() {
        return session.has("EXPERT_CONTACT_EDIT") && session.me().id().equals(expert.userId());
    }

    private Node header() {
        Label name = new Label(expert.fullName());
        name.getStyleClass().add("page-title");
        Label sub = new Label(expert.expertCode() + " · " + Fmt.label(expert.expertType()) + " · "
                + Fmt.label(expert.employmentType()));
        sub.getStyleClass().add("hint");
        HBox titleRow = new HBox(10, name, Tables.tag(expert.status()));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox left = new VBox(4, titleRow, sub);
        Label banner = banner();
        if (banner != null) left.getChildren().add(banner);
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        for (String action : expert.availableActions()) {
            Button b = switch (action) {
                case "SUSPEND", "DEACTIVATE", "RETURN" -> Ui.danger(Fmt.label(action), () -> changeStatus(action));
                case "SUBMIT", "REVIEW", "APPROVE" -> Ui.primary(Fmt.label(action), () -> changeStatus(action));
                default -> Ui.button(Fmt.label(action), () -> changeStatus(action));
            };
            actions.getChildren().add(b);
        }
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox box = new HBox(12, left, actions);
        box.getStyleClass().add("detail-header");
        return box;
    }

    /** Dòng thông báo dưới tên chuyên gia theo trạng thái (trả lại, chờ duyệt, dừng có thời hạn...). */
    private Label banner() {
        String reason = expert.statusReason();
        String text = switch (expert.status()) {
            case "DRAFT" -> reason == null ? null : "Bị trả lại, yêu cầu bổ sung: " + reason;
            case "SUBMITTED" -> "Đã trình – đang chờ Chuyên gia trưởng thẩm tra. Hồ sơ tạm khoá sửa.";
            case "REVIEWED" -> "Chuyên gia trưởng đã thẩm tra đạt – chờ GĐCN phê duyệt. Hồ sơ tạm khoá sửa."
                    + (reason == null ? "" : "\nGhi chú thẩm tra: " + reason);
            case "SUSPENDED" -> (expert.suspendedUntil() == null ? "Dừng đánh giá tới khi GĐCN mở lại"
                    : "Dừng đánh giá đến hết " + Fmt.date(expert.suspendedUntil()) + " (tự mở lại sau ngày này)")
                    + (reason == null ? "" : ". Lý do: " + reason.replaceAll("\\s*\\(dừng đến hết [^)]*\\)$", ""));
            case "INACTIVE" -> reason == null ? null : "Lý do: " + reason;
            default -> null;
        };
        if (text == null) return null;
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().addAll("banner", "DRAFT".equals(expert.status()) || "SUSPENDED".equals(expert.status())
                ? "banner-warn" : "banner-info");
        return l;
    }

    private void changeStatus(String action) {
        boolean required = switch (action) {
            case "SUBMIT", "REVIEW", "APPROVE" -> false;
            default -> true;
        };
        String label = switch (action) {
            case "RETURN" -> "Nội dung cần bổ sung / lý do trả lại";
            case "SUBMIT" -> "Ghi chú gửi Chuyên gia trưởng";
            case "REVIEW" -> "Ghi chú thẩm tra (gửi GĐCN)";
            case "APPROVE" -> "Ý kiến phê duyệt";
            default -> "Lý do";
        };
        Form f = new Form().area("comment", label, required);
        if ("SUSPEND".equals(action)) {
            f.date("until", "Dừng đến hết ngày", false)
                    .note("Để trống = dừng tới khi GĐCN mở lại. Có ngày: hết ngày đó hệ thống tự mở lại. "
                            + "Chuyên gia đang dừng sẽ không được chọn vào đoàn đánh giá.");
        }
        if ("REVIEW".equals(action)) f.note("Thẩm tra đạt: hồ sơ chuyển GĐCN phê duyệt kèm ghi chú này. "
                + "Nếu chưa đạt, dùng nút \"Trả lại / yêu cầu bổ sung\" và ghi rõ nội dung cần bổ sung.");
        if ("SUBMIT".equals(action)) f.note("Hồ sơ cần có ngày sinh, số điện thoại, ít nhất 1 học vấn và 1 kinh nghiệm. "
                + "Sau khi trình, hồ sơ bị khoá sửa trong lúc Chuyên gia trưởng thẩm tra và GĐCN phê duyệt.");
        f.showDialog(Fmt.label(action) + " – " + expert.expertCode() + " · " + expert.fullName(), Fmt.label(action),
                () -> api.changeExpertStatus(expert.id(), action, f.str("comment"),
                        "SUSPEND".equals(action) ? f.date("until") : null),
                d -> reloadHeader());
    }

    // ------------------------------------------------------------------ thông tin chung

    private Node general() {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(10);
        g.getStyleClass().add("descriptions");
        Object[][] rows = {
                {"Mã chuyên gia", expert.expertCode()}, {"Họ và tên", expert.fullName()},
                {"Ngày sinh", Fmt.date(expert.dateOfBirth())}, {"Giới tính", Fmt.label(expert.gender())},
                {"Số CCCD/CMND", expert.idNumber()}, {"Điện thoại", expert.phone()},
                {"Email", expert.email()}, {"Địa chỉ", expert.address()},
                {"Địa điểm thường trú", expert.homeLocationName()}, {"Loại chuyên gia", Fmt.label(expert.expertType())},
                {"Hình thức", Fmt.label(expert.employmentType())}, {"Phòng ban", expert.departmentName()},
                {"Chức vụ", expert.position()}, {"Ngày tham gia", Fmt.date(expert.joinedDate())},
                {"Manday tối đa/tháng", Fmt.text(expert.maxMandaysPerMonth())}, {"Tài khoản", expert.username()},
                {"Ngày tạo", Fmt.dateTime(expert.createdAt())}, {"Cập nhật", Fmt.dateTime(expert.updatedAt())}};
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label((String) rows[i][0]);
            k.getStyleClass().add("desc-key");
            Label v = new Label(rows[i][1] == null || rows[i][1].toString().isBlank() ? "—" : rows[i][1].toString());
            v.setWrapText(true);
            g.add(k, (i % 2) * 2, i / 2);
            g.add(v, (i % 2) * 2 + 1, i / 2);
        }
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        if (canEditGeneral()) {
            box.getChildren().add(Ui.toolbar(Ui.button("Sửa thông tin", () ->
                    ExpertForm.open(session, this, expert, d -> reloadHeader()))));
        } else if (canEditContact()) {
            box.getChildren().add(Ui.toolbar(Ui.button("Sửa thông tin liên hệ", this::editContact)));
        }
        if (!session.hasAll("EXPERT_VIEW")) {
            box.getChildren().add(Ui.hint("Học vấn, kinh nghiệm, code, đào tạo, chứng chỉ do Nhân viên hồ sơ cập nhật và GĐCN phê duyệt. "
                    + "Nếu có thay đổi, hãy tải tài liệu bổ sung ở tab Tài liệu hoặc liên hệ Nhân viên hồ sơ."));
        }
        box.getChildren().add(g);
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        return sp;
    }

    private void editContact() {
        Form f = new Form().text("phone", "Điện thoại", false).text("email", "Email", false).area("address", "Địa chỉ", false)
                .set("phone", expert.phone()).set("email", expert.email()).set("address", expert.address());
        f.showDialog("Sửa thông tin liên hệ", "Lưu", () -> {
            var r = expert.toRequest();
            return api.updateExpert(expert.id(), new com.npcore.ems.desktop.api.Dtos.ExpertRequest(r.fullName(),
                    r.dateOfBirth(), r.gender(), r.idNumber(), f.str("address"), f.str("phone"), f.str("email"),
                    r.expertType(), r.employmentType(), r.departmentId(), r.position(), r.joinedDate(),
                    r.homeLocationId(), r.userId(), r.maxMandaysPerMonth()));
        }, d -> reloadHeader());
    }

    // ------------------------------------------------------------------ danh sách con

    private List<Option<UUID>> evidenceOptions() {
        try {
            List<DocumentSummary> docs = api.documents(expert.id(), null, null, null, 0, 200).content();
            return Lookups.withNone(docs.stream().map(d -> new Option<>(d.id(), d.title() + " (" + d.documentTypeName() + ")")).toList());
        } catch (RuntimeException e) {
            return Lookups.withNone(List.of());
        }
    }

    private ItemsPane<Education> educations() {
        List<Option<UUID>> evidence = new ArrayList<>();
        Async.run(null, this::evidenceOptions, evidence::addAll, e -> {});
        boolean verifier = session.hasAll("EXPERT_COMPETENCY_EDIT");
        ItemsPane<Education> pane = new ItemsPane<>("Học vấn", List.of(
                Tables.col("Trình độ", Education::degreeLevelName, 110),
                Tables.col("Lĩnh vực", Education::fieldName, 160),
                Tables.col("Chuyên ngành", Education::major, 180),
                Tables.col("Trường", Education::institution, 220),
                Tables.col("Năm TN", Education::graduationYear, 70),
                Tables.col("Minh chứng", e -> e.evidenceDocumentId() == null ? "Chưa có" : "Có", 90),
                Tables.col("Đã xác nhận", e -> e.verified() ? "✓" : "", 90)),
                () -> api.expertItems(expert.id(), "educations", new TypeReference<List<Education>>() {}), canEdit(),
                existing -> {
                    Form f = new Form()
                            .choice("degree", "Trình độ", lookups.degreeOptions(), true)
                            .choice("field", "Lĩnh vực đào tạo", lookups.fieldOptions(), false)
                            .text("major", "Chuyên ngành", false)
                            .text("institution", "Trường / cơ sở đào tạo", true)
                            .text("year", "Năm tốt nghiệp", false)
                            .choice("evidence", "Tài liệu minh chứng", evidence, false);
                    if (verifier) f.check("verified", "Đã xác nhận");
                    f.note("Bằng cấp cần có tài liệu minh chứng (upload ở tab Tài liệu rồi chọn tại đây).");
                    if (existing != null) {
                        f.set("degree", existing.degreeLevelCode()).set("field", existing.fieldId())
                                .set("major", existing.major()).set("institution", existing.institution())
                                .set("year", existing.graduationYear()).set("evidence", existing.evidenceDocumentId());
                        if (verifier) f.set("verified", existing.verified());
                    }
                    return f;
                },
                (f, existing) -> {
                    Integer y = f.integer("year");
                    EducationRequest r = new EducationRequest(f.value("degree"), f.value("field"), f.str("major"),
                            f.str("institution"), y == null ? null : y.shortValue(), f.value("evidence"),
                            verifier ? f.bool("verified") : null);
                    if (existing == null) api.createExpertItem(expert.id(), "educations", r);
                    else api.updateExpertItem(expert.id(), "educations", existing.id(), r);
                },
                e -> api.deleteExpertItem(expert.id(), "educations", e.id()), Education::institution);
        pane.reload();
        return pane;
    }

    private Node experiences() {
        List<Option<UUID>> evidence = new ArrayList<>();
        Async.run(null, this::evidenceOptions, evidence::addAll, e -> {});
        ItemsPane<Experience> pane = new ItemsPane<>("Kinh nghiệm", List.of(
                Tables.col("Lĩnh vực", Experience::field, 160),
                Tables.col("Ngành", Experience::industryName, 140),
                Tables.col("Vị trí", Experience::position, 140),
                Tables.col("Tổ chức", Experience::organization, 180),
                Tables.col("Từ", e -> Fmt.date(e.fromDate()), 90),
                Tables.col("Đến", e -> e.isCurrent() ? "Hiện tại (xác nhận " + Fmt.date(e.verifiedUntil()) + ")" : Fmt.date(e.toDate()), 170),
                Tables.col("Số năm", Experience::years, 70),
                Tables.col("Số code", e -> e.codeIds().size(), 70)),
                () -> api.expertItems(expert.id(), "experiences", new TypeReference<List<Experience>>() {}), canEdit(),
                existing -> {
                    Form f = new Form()
                            .text("field", "Lĩnh vực", true)
                            .choice("industry", "Ngành", lookups.industryOptions(), false)
                            .text("position", "Vị trí", false)
                            .text("organization", "Tổ chức", false)
                            .date("from", "Từ ngày", true)
                            .check("current", "Đang làm việc")
                            .date("to", "Đến ngày", false)
                            .date("verifiedUntil", "Đã xác nhận đến ngày", false)
                            .area("description", "Mô tả", false)
                            .multi("codes", "Code liên quan", lookups.codeOptions())
                            .choice("evidence", "Tài liệu minh chứng", evidence, false)
                            .note("Số năm kinh nghiệm KHÔNG tự tăng: tính tới ngày kết thúc, hoặc tới ngày đã xác nhận "
                                    + "nếu đang làm (mặc định là ngày khai báo).");
                    if (existing != null) {
                        f.set("field", existing.field()).set("industry", existing.industryId())
                                .set("position", existing.position()).set("organization", existing.organization())
                                .set("from", existing.fromDate()).set("current", existing.isCurrent())
                                .set("to", existing.toDate()).set("verifiedUntil", existing.verifiedUntil())
                                .set("description", existing.description()).set("evidence", existing.evidenceDocumentId())
                                .selectValues("codes", existing.codeIds());
                    }
                    return f;
                },
                (f, existing) -> {
                    boolean current = f.bool("current");
                    ExperienceRequest r = new ExperienceRequest(f.value("industry"), f.str("field"), f.str("position"),
                            f.str("organization"), f.date("from"), current ? null : f.date("to"), current,
                            current ? f.date("verifiedUntil") : null, f.str("description"), f.value("evidence"),
                            f.<UUID>values("codes"));
                    if (existing == null) api.createExpertItem(expert.id(), "experiences", r);
                    else api.updateExpertItem(expert.id(), "experiences", existing.id(), r);
                },
                e -> api.deleteExpertItem(expert.id(), "experiences", e.id()), Experience::field);
        pane.reload();
        VBox box = new VBox(pane, Ui.hint("  Số năm kinh nghiệm tính tới ngày kết thúc hoặc ngày đã xác nhận – không tự tăng theo thời gian."));
        VBox.setVgrow(pane, Priority.ALWAYS);
        return box;
    }

    private ItemsPane<Training> trainings() {
        List<Option<UUID>> evidence = new ArrayList<>();
        Async.run(null, this::evidenceOptions, evidence::addAll, e -> {});
        ItemsPane<Training> pane = new ItemsPane<>("Khoá đào tạo", List.of(
                Tables.col("Tên khoá", Training::trainingName, 220),
                Tables.col("Loại", t -> Fmt.label(t.trainingType()), 140),
                Tables.col("Tiêu chuẩn", Training::standardCode, 100),
                Tables.col("Đơn vị", Training::provider, 160),
                Tables.col("Từ", t -> Fmt.date(t.fromDate()), 90),
                Tables.col("Đến", t -> Fmt.date(t.toDate()), 90),
                Tables.col("Số giờ", Training::hours, 70),
                Tables.col("Hiệu lực đến", t -> Fmt.date(t.validUntil()), 100)),
                () -> api.expertItems(expert.id(), "trainings", new TypeReference<List<Training>>() {}), canEdit(),
                existing -> {
                    Form f = new Form()
                            .text("name", "Tên khoá", true)
                            .codes("type", "Loại", List.of("LEAD_AUDITOR", "INTERNAL_AUDITOR", "TECHNICAL", "CALIBRATION", "REFRESHER", "OTHER"), false)
                            .choice("standard", "Tiêu chuẩn", lookups.standardOptions(), false)
                            .text("provider", "Đơn vị đào tạo", false)
                            .date("from", "Từ ngày", false).date("to", "Đến ngày", false)
                            .text("hours", "Số giờ", false).date("validUntil", "Hiệu lực đến", false)
                            .choice("evidence", "Tài liệu minh chứng", evidence, false);
                    if (existing != null) {
                        f.set("name", existing.trainingName()).set("type", existing.trainingType())
                                .set("standard", existing.standardId()).set("provider", existing.provider())
                                .set("from", existing.fromDate()).set("to", existing.toDate()).set("hours", existing.hours())
                                .set("validUntil", existing.validUntil()).set("evidence", existing.evidenceDocumentId());
                    }
                    return f;
                },
                (f, existing) -> {
                    TrainingRequest r = new TrainingRequest(f.str("name"), f.str("provider"), f.value("standard"),
                            f.value("type"), f.date("from"), f.date("to"), f.decimal("hours"), f.date("validUntil"),
                            existing == null ? null : existing.certificateId(), f.value("evidence"));
                    if (existing == null) api.createExpertItem(expert.id(), "trainings", r);
                    else api.updateExpertItem(expert.id(), "trainings", existing.id(), r);
                },
                t -> api.deleteExpertItem(expert.id(), "trainings", t.id()), Training::trainingName);
        pane.reload();
        return pane;
    }

    private ItemsPane<Certificate> certificates() {
        List<Option<UUID>> evidence = new ArrayList<>();
        Async.run(null, this::evidenceOptions, evidence::addAll, e -> {});
        ItemsPane<Certificate> pane = new ItemsPane<>("Chứng chỉ", List.of(
                Tables.col("Tên chứng chỉ", Certificate::certificateName, 220),
                Tables.col("Số hiệu", Certificate::certificateNo, 110),
                Tables.col("Tổ chức cấp", Certificate::issuer, 150),
                Tables.col("Tiêu chuẩn", Certificate::standardCode, 90),
                Tables.col("Ngày cấp", c -> Fmt.date(c.issuedDate()), 90),
                Tables.col("Hết hạn", c -> Fmt.date(c.expiryDate()), 90),
                Tables.status("Cảnh báo", Certificate::expiryLevel, 150),
                Tables.col("Còn (ngày)", Certificate::daysToExpiry, 80),
                Tables.status("Trạng thái", Certificate::status, 110)),
                () -> api.expertItems(expert.id(), "certificates", new TypeReference<List<Certificate>>() {}), canEdit(),
                existing -> {
                    Form f = new Form()
                            .text("name", "Tên chứng chỉ", true).text("no", "Số hiệu", false)
                            .text("issuer", "Tổ chức cấp", false)
                            .choice("standard", "Tiêu chuẩn", lookups.standardOptions(), false)
                            .date("issued", "Ngày cấp", false).date("expiry", "Ngày hết hạn", false)
                            .choice("document", "Tài liệu (bản scan)", evidence, false)
                            .codes("status", "Trạng thái", List.of("VALID", "EXPIRED", "REVOKED"), false)
                            .note("Hệ thống cảnh báo khi còn 60 / 30 / 7 ngày (ngưỡng cấu hình được).");
                    if (existing != null) {
                        f.set("name", existing.certificateName()).set("no", existing.certificateNo())
                                .set("issuer", existing.issuer()).set("standard", existing.standardId())
                                .set("issued", existing.issuedDate()).set("expiry", existing.expiryDate())
                                .set("document", existing.documentId()).set("status", existing.status());
                    }
                    return f;
                },
                (f, existing) -> {
                    CertificateRequest r = new CertificateRequest(f.str("name"), f.str("no"), f.str("issuer"),
                            f.value("standard"), f.date("issued"), f.date("expiry"), f.value("document"), f.value("status"));
                    if (existing == null) api.createExpertItem(expert.id(), "certificates", r);
                    else api.updateExpertItem(expert.id(), "certificates", existing.id(), r);
                },
                c -> api.deleteExpertItem(expert.id(), "certificates", c.id()), Certificate::certificateName);
        pane.reload();
        return pane;
    }

    private ItemsPane<Language> languages() {
        List<String> levels = List.of("BASIC", "INTERMEDIATE", "FLUENT", "NATIVE");
        ItemsPane<Language> pane = new ItemsPane<>("Ngôn ngữ", List.of(
                Tables.col("Ngôn ngữ", Language::language, 120),
                Tables.col("Mức độ", l -> Fmt.label(l.proficiency()), 160),
                Tables.col("Đánh giá được bằng ngôn ngữ này", l -> l.canAudit() ? "✓" : "", 240)),
                () -> api.expertItems(expert.id(), "languages", new TypeReference<List<Language>>() {}), canEdit(),
                existing -> {
                    Form f = new Form().text("lang", "Mã ngôn ngữ (vi, en, ja…)", true)
                            .codes("level", "Mức độ", levels, true).check("canAudit", "Đánh giá được bằng ngôn ngữ này");
                    if (existing != null) {
                        f.set("lang", existing.language()).set("level", existing.proficiency()).set("canAudit", existing.canAudit())
                                .disable("lang", true);
                    }
                    return f;
                },
                (f, existing) -> api.updateExpertItem(expert.id(), "languages", f.str("lang").toLowerCase(),
                        new LanguageRequest(f.value("level"), f.bool("canAudit"))),
                l -> api.deleteExpertItem(expert.id(), "languages", l.language()), Language::language);
        pane.reload();
        return pane;
    }

    // ------------------------------------------------------------------ tài liệu & lịch sử

    private Node documents() {
        ExpertDocumentsPane pane = new ExpertDocumentsPane(session, lookups, expert.id());
        pane.showExpert(expert.id(), expert.expertCode() + " · " + expert.fullName());
        pane.setEditable(!locked());
        pane.setPadding(new Insets(12));
        return pane;
    }

    private Node competencies() {
        TableView<ExpertCompetency> table = Tables.table("Chuyên gia chưa đăng ký năng lực nào");
        table.getColumns().addAll(List.of(
                Tables.col("Tiêu chuẩn", c -> c.definition().standardCode(), 120),
                Tables.col("Mã Code", c -> c.definition().codeValue() == null ? "(Toàn tiêu chuẩn)" : c.definition().codeValue(), 130),
                Tables.col("Vai trò", c -> c.definition().roleCode(), 90),
                Tables.col("Cấp độ", c -> Fmt.label(c.competencyLevel()), 110),
                Tables.status("Trạng thái", ExpertCompetency::status, 130),
                Tables.col("Hiệu lực từ", c -> Fmt.date(c.effectiveFrom()), 100),
                Tables.col("Hiệu lực đến", c -> Fmt.date(c.effectiveTo()), 100),
                Tables.col("Duyệt lần đầu", c -> Fmt.date(c.firstApprovedDate()), 110),
                Tables.col("Minh chứng", c -> c.evidences() == null ? "0" : String.valueOf(c.evidences().size()), 90),
                Tables.col("Ghi chú", ExpertCompetency::notes, 180)));

        Runnable loader = () -> Async.run(this, () -> api.expertCompetencies(expert.id(), null, 0, 100), page -> {
            table.getItems().setAll(page.content());
        });

        HBox bar = Ui.toolbar(Ui.button("Tải lại", loader), Ui.spacer());

        if (canEdit()) {
            Button add = Ui.primary("+ Đăng ký năng lực", () -> openAddCompetencyDialog(loader));
            bar.getChildren().add(add);
        }

        HBox actionsBar = new HBox(8);
        actionsBar.setAlignment(Pos.CENTER_LEFT);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            actionsBar.getChildren().clear();
            if (sel != null && sel.availableActions() != null) {
                for (String act : sel.availableActions()) {
                    Button b = "REVOKE".equals(act) || "SUSPEND".equals(act) || "RETURN".equals(act)
                            ? Ui.danger(Fmt.label(act), () -> runCompetencyAction(sel, act, loader))
                            : Ui.button(Fmt.label(act), () -> runCompetencyAction(sel, act, loader));
                    actionsBar.getChildren().add(b);
                }
                if (List.of("DRAFT", "NEED_REVISION").contains(sel.status()) && canEdit()) {
                    Button addEv = Ui.button("+ Thêm minh chứng", () -> openAddEvidenceDialog(sel, loader));
                    actionsBar.getChildren().add(addEv);
                }
            }
        });

        VBox box = new VBox(10, bar, actionsBar, table);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        loader.run();
        return box;
    }

    private void openAddCompetencyDialog(Runnable onDone) {
        Async.run(this, () -> {
            var stList = api.list("standards", new TypeReference<List<com.npcore.ems.desktop.api.Dtos.Standard>>() {});
            var cdList = api.list("codes", new TypeReference<List<com.npcore.ems.desktop.api.Dtos.Code>>() {});
            var roList = api.list("assessment-roles", new TypeReference<List<com.npcore.ems.desktop.api.Dtos.AssessmentRole>>() {});
            return new Object[] {stList, cdList, roList};
        }, r -> {
            var stList = (List<com.npcore.ems.desktop.api.Dtos.Standard>) r[0];
            var cdList = (List<com.npcore.ems.desktop.api.Dtos.Code>) r[1];
            var roList = (List<com.npcore.ems.desktop.api.Dtos.AssessmentRole>) r[2];

            if (stList.isEmpty()) {
                Dialogs.info("Chưa có Tiêu chuẩn nào trong hệ thống");
                return;
            }

            List<Option<UUID>> stdOptions = stList.stream()
                    .map(s -> new Option<>(s.id(), s.standardCode() + " · " + s.standardName())).toList();

            List<Option<UUID>> codeOptions = new ArrayList<>();
            codeOptions.add(new Option<>(null, "— Toàn tiêu chuẩn (Áp dụng chung / LA) —"));
            for (var c : cdList) {
                codeOptions.add(new Option<>(c.id(), "Code " + c.codeValue() + " · " + c.codeName()));
            }

            List<Option<UUID>> roleOptions = roList.stream()
                    .map(ro -> new Option<>(ro.id(), ro.roleCode() + " · " + ro.roleName())).toList();

            List<Option<String>> levelOptions = List.of(
                    new Option<>("QUALIFIED", "Đạt chuẩn (Qualified)"),
                    new Option<>("IN_TRAINING", "Đang đào tạo (In Training)"),
                    new Option<>("SENIOR", "Chuyên gia cao cấp (Senior)")
            );

            Form f = new Form()
                    .choice("std", "Tiêu chuẩn đánh giá", stdOptions, true)
                    .choice("code", "Mã ngành / Lĩnh vực (Code)", codeOptions, false)
                    .choice("role", "Vai trò trong đoàn", roleOptions, true)
                    .choice("level", "Cấp độ năng lực", levelOptions, true)
                    .date("from", "Hiệu lực từ", false)
                    .date("to", "Hiệu lực đến", false)
                    .area("notes", "Ghi chú", false);

            f.set("level", "QUALIFIED");
            if (!stdOptions.isEmpty()) f.set("std", stdOptions.get(0).value());
            if (!roleOptions.isEmpty()) f.set("role", roleOptions.get(0).value());

            f.showDialog("Đăng ký năng lực chuyên gia", "Lưu dự thảo", () -> {
                ExpertCompetencyRequest req = new ExpertCompetencyRequest(
                        null,
                        f.value("std"),
                        f.value("code"),
                        f.value("role"),
                        null,
                        f.value("level"),
                        f.date("from"),
                        f.date("to"),
                        f.str("notes")
                );
                api.addExpertCompetency(expert.id(), req);
                return Boolean.TRUE;
            }, ok -> onDone.run());
        });
    }

    private void runCompetencyAction(ExpertCompetency sel, String action, Runnable onDone) {
        boolean needComment = List.of("RETURN", "REVOKE", "SUSPEND", "REJECT").contains(action);
        Dialogs.askText(Fmt.label(action) + " năng lực", "Ghi chú / lý do", needComment).ifPresent(comment -> {
            Async.run(this, () -> api.transitionExpertCompetency(expert.id(), sel.id(), action, comment.isBlank() ? null : comment),
                    res -> onDone.run());
        });
    }

    private void openAddEvidenceDialog(ExpertCompetency sel, Runnable onDone) {
        List<Option<String>> typeOptions = List.of(
                new Option<>("EDUCATION", "Bằng cấp / Học vấn"),
                new Option<>("EXPERIENCE", "Kinh nghiệm thực tế"),
                new Option<>("TRAINING", "Khoá đào tạo"),
                new Option<>("CERTIFICATE", "Chứng chỉ nghề nghiệp"),
                new Option<>("AUDIT_LOG", "Nhật ký đánh giá"),
                new Option<>("COMPETENCE_TEST", "Bài kiểm tra năng lực"),
                new Option<>("WITNESS", "Đánh giá chứng kiến (Witness)"),
                new Option<>("INTERVIEW", "Phỏng vấn chuyên môn"),
                new Option<>("OTHER", "Khác")
        );

        Form f = new Form()
                .choice("type", "Loại minh chứng", typeOptions, true)
                .area("desc", "Mô tả minh chứng", true);

        f.set("type", "EDUCATION");

        f.showDialog("Thêm minh chứng cho năng lực", "Lưu", () -> {
            EvidenceRequest req = new EvidenceRequest(
                    null,
                    f.value("type"),
                    null,
                    null,
                    f.str("desc")
            );
            api.addCompetencyEvidence(expert.id(), sel.id(), req);
            return Boolean.TRUE;
        }, ok -> onDone.run());
    }

    private Node history() {
        TableView<HistoryEntry> table = Tables.table("Chưa có lịch sử");
        table.getColumns().addAll(List.of(
                Tables.col("Thời điểm", h -> Fmt.dateTime(h.at()), 140),
                Tables.col("Người thực hiện", HistoryEntry::actor, 140),
                Tables.col("Hành động", h -> Fmt.label(h.action()), 140),
                Tables.col("Từ", h -> Fmt.label(h.fromStatus()), 130),
                Tables.col("Sang", h -> Fmt.label(h.toStatus()), 130),
                Tables.col("Ghi chú", HistoryEntry::comment, 300)));
        Async.run(table, () -> api.expertHistory(expert.id()), rows -> table.getItems().setAll(rows));
        VBox box = new VBox(table);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }
}
