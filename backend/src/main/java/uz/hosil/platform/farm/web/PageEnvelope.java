package uz.hosil.platform.farm.web;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageEnvelope<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {
    public static <T> PageEnvelope<T> from(Page<T> page) {
        return new PageEnvelope<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
