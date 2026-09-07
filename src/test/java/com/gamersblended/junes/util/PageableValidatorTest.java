package com.gamersblended.junes.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageableValidatorTest {

    private final PageableValidator validator = new PageableValidator();

    private Pageable mockPageable(int pageNumber, int pageSize, Sort sort) {
        Pageable pageable = mock(Pageable.class);
        when(pageable.getPageNumber()).thenReturn(pageNumber);
        when(pageable.getPageSize()).thenReturn(pageSize);
        when(pageable.getSort()).thenReturn(sort);
        return pageable;
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 20, 30, 50})
    void sanitizePageable_keepsPageSize_whenWithinAllowedOptions(int pageSize) {
        Pageable pageable = mockPageable(0, pageSize, Sort.unsorted());

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getPageSize()).isEqualTo(pageSize);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 15, 25, 100})
    void sanitizePageable_replacesPageSizeWithDefault_whenNotInAllowedOptions(int pageSize) {
        Pageable pageable = mockPageable(0, pageSize, Sort.unsorted());

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getPageSize()).isEqualTo(PageableValidator.DEFAULT_PAGE_SIZE);
    }

    @Test
    void sanitizePageable_keepsPageNumber_whenNonNegative() {
        Pageable pageable = mockPageable(5, 20, Sort.unsorted());

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getPageNumber()).isEqualTo(5);
    }

    @Test
    void sanitizePageable_keepsPageNumberZero() {
        Pageable pageable = mockPageable(0, 20, Sort.unsorted());

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getPageNumber()).isZero();
    }

    @Test
    void sanitizePageable_resetsPageNumberToZero_whenNegative() {
        Pageable pageable = mockPageable(-3, 20, Sort.unsorted());

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getPageNumber()).isZero();
    }

    @Test
    void sanitizePageable_returnsUnsorted_whenInputIsUnsorted() {
        Pageable pageable = mockPageable(0, 20, Sort.unsorted());

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getSort().isUnsorted()).isTrue();
    }

    @Test
    void sanitizePageable_keepsSortOrder_whenPropertyIsAllowed() {
        Sort sort = Sort.by(Sort.Direction.DESC, "totalAmount");
        Pageable pageable = mockPageable(0, 20, sort);

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getSort().toList()).hasSize(1);
        Sort.Order order = sanitized.getSort().getOrderFor("totalAmount");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void sanitizePageable_stripsSortOrder_whenPropertyIsNotAllowed() {
        Sort sort = Sort.by(Sort.Direction.ASC, "password");
        Pageable pageable = mockPageable(0, 20, sort);

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getSort().isUnsorted()).isTrue();
    }

    @Test
    void sanitizePageable_keepsOnlyAllowedProperties_whenSortMixesValidAndInvalid() {
        Sort sort = Sort.by(
                new Sort.Order(Sort.Direction.ASC, "orderDate"),
                new Sort.Order(Sort.Direction.DESC, "notAllowed"),
                new Sort.Order(Sort.Direction.DESC, "status")
        );
        Pageable pageable = mockPageable(0, 20, sort);

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getSort().stream().map(Sort.Order::getProperty).toList())
                .containsExactly("orderDate", "status");
    }

    @Test
    void sanitizePageable_preservesSortDirectionAndOrder_forMultipleValidProperties() {
        Sort sort = Sort.by(
                new Sort.Order(Sort.Direction.DESC, "status"),
                new Sort.Order(Sort.Direction.ASC, "orderDate")
        );
        Pageable pageable = mockPageable(0, 20, sort);

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getSort().toList())
                .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("status", Sort.Direction.DESC),
                        org.assertj.core.groups.Tuple.tuple("orderDate", Sort.Direction.ASC)
                );
    }

    @Test
    void sanitizePageable_returnsUnsorted_whenAllSortPropertiesAreInvalid() {
        Sort sort = Sort.by("foo", "bar");
        Pageable pageable = mockPageable(0, 20, sort);

        Pageable sanitized = validator.sanitizePageable(pageable);

        assertThat(sanitized.getSort().isUnsorted()).isTrue();
    }
}
