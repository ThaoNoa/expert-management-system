package com.npcore.ems.shared.web;

import com.npcore.ems.shared.audit.AuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD dùng chung cho danh mục nhỏ (phòng ban, ngành, địa điểm...): list / create / update + audit log.
 * Không có delete cứng: danh mục đã dùng thì chỉ chuyển trạng thái.
 */
public abstract class SimpleCrudService<E, D, R> {

    protected final JpaRepository<E, UUID> repository;
    protected final AuditService audit;
    private final String objectType;
    private final String label;

    protected SimpleCrudService(JpaRepository<E, UUID> repository, AuditService audit, String objectType, String label) {
        this.repository = repository;
        this.audit = audit;
        this.objectType = objectType;
        this.label = label;
    }

    protected abstract D toDto(E entity);

    protected abstract E newEntity();

    protected abstract void apply(E entity, R request);

    protected abstract UUID idOf(E entity);

    /** Kiểm tra trùng mã...; excludeId = null khi tạo mới. */
    protected void validate(R request, UUID excludeId) {}

    protected Sort defaultSort() {
        return Sort.unsorted();
    }

    @Transactional(readOnly = true)
    public List<D> list() {
        return repository.findAll(defaultSort()).stream().map(this::toDto).toList();
    }

    @Transactional
    public D create(R request) {
        validate(request, null);
        E e = newEntity();
        apply(e, request);
        repository.saveAndFlush(e);
        D dto = toDto(e);
        audit.record("CREATE", objectType, idOf(e), null, dto, null);
        return dto;
    }

    @Transactional
    public D update(UUID id, R request) {
        E e = repository.findById(id).orElseThrow(() -> ApiException.notFound(label, id));
        validate(request, id);
        D before = toDto(e);
        apply(e, request);
        repository.flush();
        D after = toDto(e);
        audit.record("UPDATE", objectType, id, before, after, null);
        return after;
    }
}
