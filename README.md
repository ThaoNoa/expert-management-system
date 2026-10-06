# EMS – Expert Management System (Phase 1 – Foundation)

Phần mềm quản lý chuyên gia đánh giá: hồ sơ, năng lực, phê duyệt, matching, lập đoàn.
Repo này chứa **Phase 1** theo kế hoạch: người dùng & phân quyền, audit log, danh mục (Scheme, Tiêu chuẩn, bộ mã Code),
kho tài liệu chống trùng, hồ sơ chuyên gia và import dữ liệu cũ.

| Thư mục | Nội dung |
|---|---|
| `backend/` | Java 21, Spring Boot 3.5, Spring Security (JWT HS256), JPA/Hibernate, PostgreSQL 16, Flyway |
| `desktop/` | **Ứng dụng desktop** JavaFX 21 (cài trên Windows), gọi API của backend qua HTTP |
| `backend/src/main/resources/db/migration/` | Flyway V1–V12: toàn bộ schema (cả các bảng Phase 2–4) + dữ liệu khởi tạo |
| `db-tests/` | 23 ca test nghiệp vụ chạy thẳng trên PostgreSQL (state machine, coverage, matching, audit…) |
| `docs/api-phase1.md` | Hợp đồng API giữa backend và ứng dụng desktop |
| `docker-compose.yml` | Máy chủ: PostgreSQL, Redis, backend (MinIO tuỳ chọn) |

## Kiến trúc

```
[EMS.exe trên máy người dùng]  ──HTTP/JSON──►  [Backend Spring Boot :8080]  ──►  [PostgreSQL 16] + [Redis] + [kho file]
   (JavaFX, nhiều máy)                           (1 máy chủ trong mạng nội bộ)
```

Mọi nghiệp vụ, phân quyền, audit đều ở backend; ứng dụng desktop chỉ là giao diện nên nhiều máy dùng chung một dữ liệu.

## 1. Chạy máy chủ (Docker)

```bash
docker compose up -d --build
```

- API: http://localhost:8080/api/v1 — Swagger: http://localhost:8080/swagger-ui.html
- Tài khoản ban đầu: `admin` / `Admin@12345` (đổi ngay sau khi đăng nhập; đặt `EMS_ADMIN_PASSWORD`, `EMS_JWT_SECRET` khi triển khai thật).

> `admin` (SUPER_ADMIN) chỉ quản trị hệ thống: tạo user, phân quyền, cấu hình, danh mục. Theo nguyên tắc tách biệt nhiệm vụ,
> SUPER_ADMIN **không** tạo chuyên gia hay phê duyệt. Hãy tạo user với role `CERTIFICATION_MANAGER` (lập hồ sơ),
> `DOCUMENT_CONTROLLER` (tài liệu), `CERTIFICATION_DIRECTOR` (GĐCN: phê duyệt / trả lại hồ sơ, dừng / mở chuyên gia).
>
> Quy trình hồ sơ năng lực: **NV hồ sơ lập hồ sơ → Trình → Chuyên gia trưởng thẩm tra** (đạt kèm ghi chú / trả lại)
> **→ GĐCN Phê duyệt** (Đang hoạt động) hoặc **Trả lại / yêu cầu bổ sung** (về Nháp, kèm nội dung). GĐCN có thể **dừng đánh giá có thời hạn**; hết hạn hệ thống tự mở lại.

## 2. Ứng dụng desktop

**Chạy khi phát triển (IntelliJ):** mở `pom.xml` ở thư mục gốc → chạy class `com.npcore.ems.desktop.Launcher`
(module `desktop`). Hoặc dòng lệnh: `mvn -pl desktop package` rồi `java -jar desktop/target/ems-desktop.jar`.

**Đóng gói cho người dùng (Windows, PowerShell):**

```powershell
cd desktop
.\build-installer.ps1              # → target\installer\EMS\EMS.exe (thư mục chạy ngay, đã kèm Java)
.\build-installer.ps1 -Type msi    # → bộ cài EMS-1.0.0.msi (cần WiX Toolset 3.14 trong PATH)
```

Máy cài không cần Java. Lần đầu mở, ở màn hình đăng nhập bấm **Máy chủ** để nhập địa chỉ backend
(vd. `http://192.168.1.10:8080`); giá trị được lưu trong `%USERPROFILE%\.ems\desktop.properties`.
Có thể đặt sẵn bằng biến môi trường `EMS_SERVER_URL`.

> Triển khai thật nên đặt backend sau HTTPS (reverse proxy) và mở cổng 8080/443 trong mạng nội bộ cho các máy dùng app.

## Phát triển backend (IntelliJ IDEA)

Yêu cầu: JDK 21, Maven 3.9+, PostgreSQL 16 (hoặc `docker compose up -d postgres`).

1. Tạo DB: `createdb ems` (user/password mặc định `ems`/`ems`, đổi bằng `EMS_DB_URL`, `EMS_DB_USER`, `EMS_DB_PASSWORD`).
   User DB cần quyền tạo extension (`btree_gist`, `pg_trgm`, `unaccent`).
2. Backend: mở `backend/pom.xml` trong IntelliJ → chạy `EmsApplication`, hoặc `cd backend && mvn spring-boot:run`.
   Flyway tự chạy migration, sau đó tạo tài khoản `admin`.
3. Desktop: chạy `Launcher` như mục 2 (mặc định kết nối `http://localhost:8080`).

Biến môi trường chính của backend: xem `backend/src/main/resources/application.yml`
(`EMS_DB_*`, `EMS_JWT_SECRET`, `EMS_STORAGE_TYPE=local|minio`, `EMS_CACHE_TYPE=simple|redis`, `EMS_CORS_ORIGINS`).

## Kiểm thử

```bash
mvn verify       # từ thư mục gốc: backend 65 test (Testcontainers PostgreSQL 16, cần Docker) + desktop 9 test
```

Không có Docker: dùng một PostgreSQL có sẵn được tạo bởi chính ứng dụng (Flyway):
`EMS_TEST_DB_URL=jdbc:postgresql://localhost:5432/ems_test EMS_TEST_DB_USER=ems EMS_TEST_DB_PASSWORD=ems mvn verify`.

Profile `offline` (`mvn -o -Poffline verify`) dành cho môi trường không tới được Maven Central: bỏ Flyway, POI (Excel),
MinIO, Swagger, Testcontainers; import chỉ nhận CSV; DB test phải migrate sẵn bằng `psql -f`. Bản trong repo đã được
build và chạy 65/65 test theo profile này; **cần chạy `mvn verify` (profile full) một lần trên máy có Internet** để
xác nhận phần dùng các thư viện còn lại.

## Đã có trong Phase 1

- Đăng nhập JWT + refresh token xoay vòng, khoá tài khoản sau 5 lần sai, chính sách mật khẩu, đổi / reset mật khẩu.
- Role & permission cấu hình được (không hard-code tên role), phạm vi dữ liệu ALL / OWN; chuyên gia chỉ thấy hồ sơ của mình.
- Audit log append-only có chuỗi hash (DB chặn UPDATE/DELETE), lịch sử phê duyệt dùng chung.
- State machine cấu hình trong bảng `workflow_transitions`, kiểm tra ở service và trigger DB.
- Danh mục: phòng ban, vai trò đánh giá, scheme (cờ code cha bao code con), tiêu chuẩn + phiên bản,
  bộ mã có version + cây code + import Excel/CSV (all-or-nothing), ngành, hoạt động, địa điểm, lĩnh vực đào tạo.
- Kho tài liệu: chống trùng SHA-256, version, liên kết một tài liệu cho nhiều đối tượng, xác minh bởi người khác người upload.
- Hồ sơ chuyên gia: mã FT-/PT- tự sinh theo cấu hình, học vấn, kinh nghiệm (không tự tăng số năm), đào tạo,
  chứng chỉ có cảnh báo hết hạn 60/30/7 ngày (cấu hình), ngôn ngữ, trạng thái Nháp / Chờ GĐCN phê duyệt / Hoạt động / Tạm dừng (có thời hạn) / Ngừng,
  tìm kiếm không dấu phía server, import hồ sơ cũ (báo lỗi từng dòng).

## Ứng dụng desktop gồm

Đăng nhập (tự làm mới token, hết phiên quay về đăng nhập), tổng quan, menu theo quyền; chuyên gia (danh sách lọc / phân trang
phía server, thêm / sửa, học vấn, kinh nghiệm + code, đào tạo, chứng chỉ có mức cảnh báo, ngôn ngữ, tài liệu, lịch sử,
nút chuyển trạng thái theo quyền); import chuyên gia (tải mẫu, báo lỗi từng dòng); kho tài liệu (upload chống trùng,
version, tải về, xác minh / từ chối); toàn bộ danh mục; quản trị người dùng, vai trò & ma trận quyền, cấu hình, nhật ký.

## Giới hạn đã biết

- Access token còn hiệu lực tối đa 15 phút sau khi tài khoản bị khoá/xoá (refresh token bị thu hồi ngay).
- Upload thành công nhưng transaction lỗi có thể để lại file mồ côi trong storage (cần job dọn dẹp ở Phase 2).
- Batch gửi cảnh báo hết hạn qua email / in-app thuộc Sprint 10 (Phase 2); hiện chỉ hiển thị mức cảnh báo trên giao diện.
