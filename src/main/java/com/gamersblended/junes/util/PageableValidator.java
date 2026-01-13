package com.gamersblended.junes.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class PageableValidator {

    public static final Integer DEFAULT_PAGE_SIZE = 20;
    public static final Set<Integer> PAGE_SIZE_OPTIONS = Set.of(10, 20, 30, 50);
    public static final Set<String> SORT_OPTIONS = Set.of("order_date", "total_amount", "status");

    public Pageable sanitizePageable(Pageable pageable) {
        int pageSize = pageable.getPageSize();
        int pageNumber = pageable.getPageNumber();

        // Page size
        if (!PAGE_SIZE_OPTIONS.contains(pageSize)) {
            pageSize = DEFAULT_PAGE_SIZE;
        }

        // Page number
        if (pageNumber < 0) {
            pageNumber = 0;
        }

        // Sort by
        List<Sort.Order> validOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (SORT_OPTIONS.contains(order.getProperty())) {
                validOrders.add(order);
            }
        }

        Sort validSort = validOrders.isEmpty() ? Sort.unsorted() : Sort.by(validOrders);

        return PageRequest.of(pageNumber, pageSize, validSort);
    }
}
