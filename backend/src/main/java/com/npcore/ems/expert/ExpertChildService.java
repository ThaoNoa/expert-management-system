package com.npcore.ems.expert;

import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** CRUD dùng chung cho các danh sách con của hồ sơ chuyên gia, có kiểm tra phạm vi dữ liệu + audit. */
public abstract class ExpertChildService<E extends ExpertChild, D, R> {

    protected final ExpertAccess access;
    protected final AuditService audit;
    private final JpaRepository<E, UUID> repository;
    private final String objectType;
    private final String label;

    protected ExpertChildService(ExpertAccess access, AuditService audit, JpaRepository<E, UUID> repository,
                                 String objectType, String label) {
        this.access = access;
        this.audit = audit;
        this.repository = repository;
        this.objectType = objectType;
        this.label = label;
    }

    protected abstract List<E> findByExpert(UUID expertId);

    protected abstract E newEntity();

    protected abstract void apply(E entity, R request);

    protected abstract D toDto(E entity);

    @Transactional(readOnly = true)
    public List<D> list(UUID expertId) {
        access.requireView(expertId);
        return findByExpert(expertId).stream().map(this::toDto).toList();
    }

    @Transactional
    public D create(UUID expertId, R request) {
        access.requireEdit(expertId);
        E e = newEntity();
        e.setExpertId(expertId);
        apply(e, request);
        repository.saveAndFlush(e);
        D dto = toDto(e);
        audit.record("CREATE", objectType, e.getId(), null, dto, "expert=" + expertId);
        return dto;
    }

    @Transactional
    public D update(UUID expertId, UUID itemId, R request) {
        access.requireEdit(expertId);
        E e = load(expertId, itemId);
        D before = toDto(e);
        apply(e, request);
        repository.flush();
        D after = toDto(e);
        audit.record("UPDATE", objectType, itemId, before, after, "expert=" + expertId);
        return after;
    }

    @Transactional
    public void delete(UUID expertId, UUID itemId) {
        access.requireEdit(expertId);
        E e = load(expertId, itemId);
        audit.record("DELETE", objectType, itemId, toDto(e), null, "expert=" + expertId);
        repository.delete(e);
    }

    private E load(UUID expertId, UUID itemId) {
        return repository.findById(itemId).filter(x -> x.getExpertId().equals(expertId))
                .orElseThrow(() -> ApiException.notFound(label, itemId));
    }
}
