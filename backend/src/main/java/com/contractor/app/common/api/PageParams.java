package com.contractor.app.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageParams {

    public static final int MAX_SIZE = 100;

    private PageParams() {
    }

    /** يقيّد الحجم بين 1 و100 ويضيف id للترتيب كي لا يتغير ترتيب الصفحات. */
    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        Sort ordered = sort.getOrderFor("id") == null ? sort.and(Sort.by("id").ascending()) : sort;
        return PageRequest.of(safePage, safeSize, ordered);
    }
}
