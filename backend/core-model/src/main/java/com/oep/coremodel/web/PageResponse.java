package com.oep.coremodel.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Stable JSON shape for server-side pagination (page is 0-based). */
public record PageResponse<T>(List<T> items, long total, int page, int size, int totalPages) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getTotalElements(),
                page.getNumber(), page.getSize(), page.getTotalPages());
    }
}
