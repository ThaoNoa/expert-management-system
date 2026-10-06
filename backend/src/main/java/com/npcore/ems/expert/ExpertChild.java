package com.npcore.ems.expert;

import java.util.UUID;

/** Bản ghi con thuộc hồ sơ chuyên gia (học vấn, kinh nghiệm, đào tạo, chứng chỉ). */
public interface ExpertChild {
    UUID getId();

    UUID getExpertId();

    void setExpertId(UUID expertId);
}
