package com.npcore.ems.document;

import com.npcore.ems.document.DocumentDtos.DocumentDetail;
import com.npcore.ems.document.DocumentDtos.DocumentSummary;
import com.npcore.ems.document.DocumentDtos.LinkDto;
import com.npcore.ems.document.DocumentDtos.UploadResult;
import com.npcore.ems.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService service;

    @GetMapping("/document-types")
    public List<DocumentDtos.DocumentTypeDto> types() {
        return service.types();
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public ResponseEntity<UploadResult> upload(@RequestPart("file") MultipartFile file,
                                               @RequestParam String documentTypeCode,
                                               @RequestParam(required = false) String title,
                                               @RequestParam(required = false) UUID ownerExpertId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDate,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                                               @RequestParam(required = false) String linkObjectType,
                                               @RequestParam(required = false) UUID linkObjectId) {
        UploadResult r = service.upload(new DocumentService.UploadCommand(file, documentTypeCode, title, ownerExpertId,
                issuedDate, expiryDate, linkObjectType, linkObjectId));
        return ResponseEntity.status(r.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(r);
    }

    @GetMapping("/documents")
    public PageResponse<DocumentSummary> search(@RequestParam(required = false) UUID ownerExpertId,
                                                @RequestParam(required = false) String documentTypeCode,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String q,
                                                @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.search(ownerExpertId, documentTypeCode, status, q, pageable);
    }

    @GetMapping("/documents/{id}")
    public DocumentDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping(path = "/documents/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public DocumentDetail newVersion(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
        return service.newVersion(id, file, issuedDate, expiryDate);
    }

    @GetMapping("/document-versions/{versionId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID versionId) {
        DocumentService.Download d = service.download(versionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.size())
                .body(new InputStreamResource(d.content()));
    }

    @PostMapping("/document-versions/{versionId}/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DOCUMENT_VERIFY')")
    public void verify(@PathVariable UUID versionId) {
        service.verify(versionId);
    }

    @PostMapping("/document-versions/{versionId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DOCUMENT_VERIFY')")
    public void reject(@PathVariable UUID versionId, @RequestBody @Valid DocumentDtos.RejectRequest req) {
        service.reject(versionId, req.reason());
    }

    @PostMapping("/documents/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public LinkDto addLink(@PathVariable UUID id, @RequestBody @Valid DocumentDtos.LinkRequest req) {
        return service.addLink(id, req);
    }

    @DeleteMapping("/document-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public void removeLink(@PathVariable UUID linkId) {
        service.removeLink(linkId);
    }
}
