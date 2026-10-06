package com.npcore.ems.desktop.api;

import java.util.List;

public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> Page<T> empty() {
        return new Page<>(List.of(), 0, 0, 0, 0);
    }
}
