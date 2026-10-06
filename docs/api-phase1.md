# EMS – API Phase 1 (contract giữa backend và frontend)

Base URL: `/api/v1`. JSON camelCase. Ngày: `YYYY-MM-DD`; thời điểm: ISO-8601 (`2026-10-05T09:00:00Z`). ID: UUID string.
Auth: header `Authorization: Bearer <accessToken>`.

## Quy ước chung

**Page** (mọi API danh sách có `page`, `size`, `sort`):
```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }
```
`page` bắt đầu từ 0. `sort=fullName,asc`.

**Lỗi** (RFC 7807 ProblemDetail):
```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Validation failed",
  "code": "VALIDATION_ERROR", "errors": [ { "field": "email", "message": "must be a well-formed email address" } ] }
```
`code` thường gặp: `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404),
`CONFLICT` / `DUPLICATE` (409), `ILLEGAL_TRANSITION` (409), `BUSINESS_RULE` (422).

**ImportResult** (import file):
```json
{ "total": 120, "imported": 117, "skipped": 0, "errors": [ { "row": 5, "message": "Mã cha 'X' không tồn tại" } ] }
```

**Lookup** tối giản: `{ "id": "...", "code": "...", "name": "..." }`.

## 1. Auth
| Method | Path | Body | Trả về |
|---|---|---|---|
| POST | /auth/login | `{username, password}` | `TokenResponse` |
| POST | /auth/refresh | `{refreshToken}` | `TokenResponse` |
| POST | /auth/logout | `{refreshToken}` | 204 |
| GET | /auth/me | | `CurrentUser` |
| POST | /auth/change-password | `{currentPassword, newPassword}` | 204 |

`TokenResponse = {accessToken, refreshToken, expiresIn (giây), user: CurrentUser}`
`CurrentUser = {id, username, fullName, email, roles: string[], permissions: string[], expertId: string|null}`

`permissions` là mã quyền (VD `EXPERT_VIEW`). Nếu quyền có phạm vi toàn bộ thì có thêm `EXPERT_VIEW:ALL`;
chỉ có `EXPERT_VIEW` mà không có `:ALL` nghĩa là chỉ xem dữ liệu của mình (role EXPERT).
Access token sống 15 phút → FE gọi `/auth/refresh` khi gặp 401, thất bại thì về trang login.

## 2. Users, Roles, Permissions (quyền `USER_MANAGE`)
| Method | Path | Ghi chú |
|---|---|---|
| GET | /users?q=&status=&page=&size= | `Page<User>` |
| POST | /users | `{username, email, fullName, departmentId?, position?, password, roleCodes[]}` |
| GET | /users/{id} | `User` |
| PUT | /users/{id} | `{email, fullName, departmentId?, position?, roleCodes[]}` |
| POST | /users/{id}/disable · /users/{id}/enable | 204 |
| POST | /users/{id}/reset-password | `{newPassword}` |
| DELETE | /users/{id} | soft delete, 204 |
| GET | /roles | `Role[]` |
| POST | /roles · PUT /roles/{id} | `{roleCode, roleName, description, permissions: [{code, dataScope: ALL|DEPARTMENT|OWN}]}` |
| DELETE | /roles/{id} | 409 nếu đang gán cho user hoặc là role hệ thống |
| GET | /permissions | `[{code, name, module}]` |

`User = {id, username, email, fullName, departmentId, departmentName, position, status: ACTIVE|DISABLED|LOCKED, roleCodes[], lastLoginAt, createdAt}`
`Role = {id, roleCode, roleName, description, system: boolean, permissions: [{code, dataScope}]}`

## 3. Master data
Đọc: mọi user đã đăng nhập. Ghi: `MASTER_DATA_MANAGE`.

| Resource | Endpoints | Fields |
|---|---|---|
| Departments | GET/POST /departments, PUT /departments/{id} | `{id, departmentCode, departmentName, parentId, status}` |
| Assessment roles | GET/POST /assessment-roles, PUT /assessment-roles/{id} | `{id, roleCode, roleName, description, countsForCoverage, sortOrder, status}` |
| Schemes | GET/POST /schemes, PUT /schemes/{id} | `{id, schemeCode, schemeName, description, parentCoversChild, status}` |
| Standards | GET /standards?schemeId=, POST, PUT /standards/{id} | `{id, standardCode, standardName, schemeId, schemeCode, status}` |
| Standard versions | GET/POST /standards/{id}/versions, PUT /standard-versions/{id} | `{id, standardId, version, effectiveFrom, effectiveTo, transitionEnd, status: DRAFT|ACTIVE|TRANSITION|WITHDRAWN}` |
| Code sets | GET /code-sets?schemeId=, POST /code-sets, POST /code-sets/{id}/activate | `{id, schemeId, schemeCode, version, effectiveFrom, effectiveTo, sourceRef, status: DRAFT|ACTIVE|RETIRED, codeCount}` |
| Codes | GET /code-sets/{id}/codes, POST /code-sets/{id}/codes, PUT /codes/{id} | `{id, codeSetId, codeValue, codeName, parentId, parentCode, level, path, riskCategory, status}`; POST body: `{codeValue, codeName, parentCode?, riskCategory?}` |
| Code import | POST /code-sets/{id}/import (multipart `file`: .xlsx hoặc .csv) | cột: `code_value, code_name, parent_code, risk_category` → `ImportResult` |
| Industries | GET/POST /industries, PUT /industries/{id} | `{id, industryCode, industryName, description}` |
| Activities | GET/POST /activities, PUT /activities/{id} | `{id, activityCode, activityName, description}` |
| Locations | GET/POST /locations, PUT /locations/{id} | `{id, locationName, province, country, region, latitude, longitude}` |
| Education fields | GET/POST /education-fields, PUT /education-fields/{id} | `{id, fieldCode, fieldName, parentId}` |
| Degree levels | GET /degree-levels | `{code, name, rankOrder}` |
| Document types | GET /document-types | `{code, name, requiresExpiry, requiresVerification}` |

Code set chỉ sửa / thêm code / import khi `status = DRAFT`. Activate: bộ ACTIVE cũ của scheme chuyển RETIRED.

## 4. Documents
Quyền: `DOCUMENT_MANAGE` (upload/link), `DOCUMENT_VERIFY` (xác minh). Expert (scope OWN) chỉ thấy tài liệu của mình.

| Method | Path | Ghi chú |
|---|---|---|
| POST | /documents (multipart) | `file`, `documentTypeCode`, `title`, `ownerExpertId?`, `issuedDate?`, `expiryDate?`, `linkObjectType?`, `linkObjectId?` → `UploadResult` |
| GET | /documents?ownerExpertId=&documentTypeCode=&status=&q=&page= | `Page<DocumentSummary>` |
| GET | /documents/{id} | `DocumentDetail` |
| POST | /documents/{id}/versions (multipart) | `file`, `issuedDate?`, `expiryDate?` → `DocumentDetail` |
| GET | /document-versions/{versionId}/download | file stream |
| POST | /document-versions/{versionId}/verify | 204 – người xác minh ≠ người upload |
| POST | /document-versions/{versionId}/reject | `{reason}` |
| POST | /documents/{id}/links | `{objectType, objectId, purpose?}` |
| DELETE | /document-links/{linkId} | 204 |

`UploadResult = {duplicate: boolean, document: DocumentDetail}` — trùng SHA-256 thì không lưu file mới, trả document đã có (và tạo link nếu có `linkObjectType`).
`DocumentSummary = {id, title, documentTypeCode, documentTypeName, ownerExpertId, ownerExpertName, currentVersion: Version, status, createdAt}`
`DocumentDetail = DocumentSummary + {versions: Version[], links: [{id, objectType, objectId, purpose, linkedAt}]}`
`Version = {id, versionNo, fileName, contentType, fileSize, sha256, issuedDate, expiryDate, status: PENDING_VERIFICATION|VERIFIED|REJECTED|SUPERSEDED|EXPIRED, uploadedBy, uploadedAt, verifiedBy, verifiedAt, rejectReason}`

`objectType`: EXPERT, EDUCATION, EXPERIENCE, TRAINING, CERTIFICATE, ...

## 5. Experts
Quyền: `EXPERT_VIEW` (`:ALL` hoặc chỉ của mình), `EXPERT_CREATE`, `EXPERT_EDIT`, `EXPERT_APPROVE`, `EXPERT_SUSPEND`.

| Method | Path | Ghi chú |
|---|---|---|
| GET | /experts?q=&expertType=&employmentType=&status=&departmentId=&page=&size=&sort= | `Page<ExpertSummary>`; `q` tìm theo tên (không dấu) hoặc mã |
| POST | /experts | `ExpertRequest` → `ExpertDetail` (mã FT-/PT- tự sinh) |
| GET | /experts/me | `ExpertDetail` của user đang đăng nhập (404 nếu chưa gắn) |
| GET | /experts/{id} | `ExpertDetail` |
| PUT | /experts/{id} | `ExpertRequest` |
| POST | /experts/{id}/status | `{action: SUBMIT|APPROVE|RETURN|SUSPEND|REINSTATE|DEACTIVATE|REACTIVATE, comment, suspendedUntil?: date}` |
| GET | /experts/{id}/history | `[{at, actor, action, fromStatus, toStatus, comment}]` |
| GET/POST | /experts/{id}/educations | `Education` |
| PUT/DELETE | /experts/{id}/educations/{itemId} | |
| GET/POST, PUT/DELETE | /experts/{id}/experiences[/{itemId}] | `Experience` |
| GET/POST, PUT/DELETE | /experts/{id}/trainings[/{itemId}] | `Training` |
| GET/POST, PUT/DELETE | /experts/{id}/certificates[/{itemId}] | `Certificate` |
| GET, PUT/DELETE | /experts/{id}/languages[/{language}] | `Language` (PUT tạo hoặc sửa) |
| POST | /experts/import (multipart `file`) | → `ImportResult` |
| GET | /experts/import/template | file CSV mẫu |

`ExpertRequest = {fullName, dateOfBirth?, gender?: MALE|FEMALE|OTHER, idNumber?, address?, phone?, email?, expertType: AUDITOR|TECHNICAL_EXPERT|BOTH, employmentType: FULLTIME|PARTTIME, departmentId?, position?, joinedDate?, homeLocationId?, userId?, maxMandaysPerMonth?}`
`ExpertSummary = {id, expertCode, fullName, expertType, employmentType, status, departmentName, email, phone, updatedAt}`
`ExpertDetail = ExpertSummary + ExpertRequest fields + {departmentName, homeLocationName, userId, username, statusReason, suspendedUntil, availableActions: string[] (các action trạng thái user hiện tại được làm, VD ["SUSPEND","DEACTIVATE"]), createdAt, counts: {educations, experiences, trainings, certificates, documents}}`

`Education = {id, degreeLevelCode, degreeLevelName, fieldId, fieldName, major, institution, graduationYear, evidenceDocumentId, verified}`
`Experience = {id, industryId, industryName, field, position, organization, fromDate, toDate, isCurrent, verifiedUntil, description, evidenceDocumentId, years (số, 1 chữ số thập phân), codeIds: string[]}`
  - Quy tắc: `isCurrent=true` ⇒ `toDate` null; số năm tính đến `toDate` hoặc `verifiedUntil`, KHÔNG tự tăng.
`Training = {id, trainingName, provider, standardId, standardCode, trainingType: LEAD_AUDITOR|INTERNAL_AUDITOR|TECHNICAL|CALIBRATION|REFRESHER|OTHER, fromDate, toDate, hours, validUntil, certificateId, evidenceDocumentId}`
`Certificate = {id, certificateName, certificateNo, issuer, standardId, standardCode, issuedDate, expiryDate, documentId, status: VALID|EXPIRED|REVOKED, expiryLevel: NONE|WARNING|HIGH|CRITICAL|EXPIRED, daysToExpiry}`
`Language = {language (ISO 639-1), proficiency: BASIC|INTERMEDIATE|FLUENT|NATIVE, canAudit}`

Expert status (V13 – quy trình VinaCert):
- DRAFT --SUBMIT (NV hồ sơ, EXPERT_EDIT; cần ngày sinh, SĐT, ≥1 học vấn, ≥1 kinh nghiệm)--> SUBMITTED (khoá sửa)
- SUBMITTED --APPROVE (GĐCN, EXPERT_APPROVE; người trình không được tự duyệt)--> ACTIVE
- SUBMITTED --RETURN (GĐCN, bắt buộc comment = nội dung cần bổ sung)--> DRAFT
- ACTIVE --SUSPEND (bắt buộc lý do, `suspendedUntil` tuỳ chọn = ngày cuối bị dừng)--> SUSPENDED; hết hạn hệ thống tự REINSTATE (job 00:05 hằng ngày)
- SUSPENDED --REINSTATE--> ACTIVE; ACTIVE/SUSPENDED --DEACTIVATE--> INACTIVE --REACTIVATE--> ACTIVE

## 6. Audit log (quyền `AUDIT_LOG_VIEW`)
GET /audit-logs?objectType=&objectId=&userId=&action=&from=&to=&page=&size= → `Page<{id, occurredAt, userId, username, action, objectType, objectId, fromValue, toValue, reason, ipAddress}>`

## 7. Settings
GET /settings → `[{key, value (JSON: số/chuỗi/bool), valueType, category, description}]` (quyền `SETTING_MANAGE`); PUT /settings/{key} `{value}`.
