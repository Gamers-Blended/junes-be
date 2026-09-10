package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.exception.InvalidProductQueryException;
import com.gamersblended.junes.model.Product;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomProductRepositoryImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private CustomProductRepositoryImpl repository;

    private static final String VALID_PLATFORM = "ps5";
    private static final Pageable VALID_PAGEABLE = PageRequest.of(0, 10);
    private static final String CURRENT_DATE = "2026-09-10";
    private static final String IN_STOCK = "in_stock";
    private static final String OUT_OF_STOCK = "out_of_stock";
    private static final String PREORDER = "preorder";
    private static final String GENRE_ACTION = "Action";
    private static final String CANNOT_EXCEED_MSG = "cannot exceed";
    private static final String INVALID_CHARACTERS_MSG = "invalid characters";
    private static final String LIMIT_MUST_BE_BETWEEN_MSG = "limit must be between";

    @BeforeEach
    void setUp() {
        repository = new CustomProductRepositoryImpl(mongoTemplate);
    }

    private Query captureFindQuery() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Product.class));
        return captor.getValue();
    }

    // ---------- findProductsWithFilters: validation ----------

    @Test
    void findProductsWithFilters_nullPlatform_throws() {
        assertThatThrownBy(() -> repository.findProductsWithFilters(
                null, null, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("Platform");
    }

    @Test
    void findProductsWithFilters_blankPlatform_throws() {
        assertThatThrownBy(() -> repository.findProductsWithFilters(
                "   ", null, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("Platform");
    }

    @Test
    void findProductsWithFilters_invalidPlatformValue_throws() {
        assertThatThrownBy(() -> repository.findProductsWithFilters(
                "not-a-real-platform", null, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("not valid");
    }

    @Test
    void findProductsWithFilters_nullPageable_throws() {
        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, null))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("Pageable");
    }

    @Test
    void findProductsWithFilters_pageSizeExceedsLimit_throws() {
        Pageable oversizedPage = PageRequest.of(0, 101);

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, oversizedPage))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("Page size");
    }

    @Test
    void findProductsWithFilters_nameTooLong_throws() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, tooLong, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("name");
    }

    @Test
    void findProductsWithFilters_nameWithUnsafeCharacters_throws() {
        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, "mario<script>", null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(INVALID_CHARACTERS_MSG);
    }

    @Test
    void findProductsWithFilters_negativePrice_throws() {
        BigDecimal negativePrice = new BigDecimal("-1.00");

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, negativePrice, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void findProductsWithFilters_priceExceedsMax_throws() {
        BigDecimal tooExpensive = new BigDecimal("1000000.00");

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, tooExpensive, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(CANNOT_EXCEED_MSG);
    }

    @Test
    void findProductsWithFilters_priceWithTooManyDecimals_throws() {
        BigDecimal tooManyDecimals = new BigDecimal("19.999");

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, tooManyDecimals, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    void findProductsWithFilters_minPriceGreaterThanMaxPrice_throws() {
        BigDecimal minPrice = new BigDecimal("50.00");
        BigDecimal maxPrice = new BigDecimal("10.00");

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, minPrice, maxPrice, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("cannot be greater than");
    }

    @Test
    void findProductsWithFilters_listExceedsMaxSize_throws() {
        List<String> tooManyGenres = java.util.Collections.nCopies(21, GENRE_ACTION);

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, tooManyGenres, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(CANNOT_EXCEED_MSG);
    }

    @Test
    void findProductsWithFilters_listContainingNull_throws() {
        List<String> genresWithNull = java.util.Arrays.asList(GENRE_ACTION, null);

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, genresWithNull, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("cannot contain null");
    }

    @Test
    void findProductsWithFilters_listItemWithUnsafeCharacters_throws() {
        List<String> unsafeGenres = List.of("Action<script>");

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, unsafeGenres, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(INVALID_CHARACTERS_MSG);
    }

    @Test
    void findProductsWithFilters_startingLetterNotSingleCharacter_throws() {
        List<String> invalidStartingLetters = List.of("ab");

        assertThatThrownBy(() -> repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, invalidStartingLetters, null, CURRENT_DATE, VALID_PAGEABLE))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("exactly one character");
    }

    // ---------- findProductsWithFilters: query construction ----------

    @Test
    void findProductsWithFilters_platformOnly_addsPlatformCriteriaOnly() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();

        assertThat(queryObject)
                .containsEntry("platform", VALID_PLATFORM)
                .hasSize(1);
    }

    @Test
    void findProductsWithFilters_nameFilter_addsCaseInsensitiveContainsRegex() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, "  Mario  ", null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();

        Pattern namePattern = (Pattern) queryObject.get("name");
        assertThat(namePattern.pattern()).isEqualTo(".*Mario.*");
        assertThat(namePattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
    }

    @Test
    void findProductsWithFilters_minAndMaxPrice_addsPriceRangeCriteria() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, new BigDecimal("10.00"), new BigDecimal("50.00"), null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document priceDoc = (Document) captureFindQuery().getQueryObject().get("price");

        assertThat(priceDoc)
                .containsEntry("$gte", 10.0)
                .containsEntry("$lte", 50.0);
    }

    @Test
    void findProductsWithFilters_minPriceOnly_addsGteCriteriaOnly() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, new BigDecimal("10.00"), null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document priceDoc = (Document) captureFindQuery().getQueryObject().get("price");

        assertThat(priceDoc).containsEntry("$gte", 10.0);
        assertThat(priceDoc.containsKey("$lte")).isFalse();
    }

    @Test
    void findProductsWithFilters_maxPriceOnly_addsLteCriteriaOnly() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, new BigDecimal("50.00"), null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document priceDoc = (Document) captureFindQuery().getQueryObject().get("price");

        assertThat(priceDoc).containsEntry("$lte", 50.0);
        assertThat(priceDoc.containsKey("$gte")).isFalse();
    }

    @Test
    void findProductsWithFilters_genresFilter_addsInCriteria() {
        List<String> genres = List.of(GENRE_ACTION, "RPG");

        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, genres, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document genresDoc = (Document) captureFindQuery().getQueryObject().get("genres");

        assertThat(genresDoc.getList("$in", String.class)).containsExactlyInAnyOrderElementsOf(genres);
    }

    @Test
    void findProductsWithFilters_regionsFilter_addsInCriteria() {
        List<String> regions = List.of("US", "EU");

        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, regions, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document regionDoc = (Document) captureFindQuery().getQueryObject().get("region");

        assertThat(regionDoc.getList("$in", String.class)).containsExactlyInAnyOrderElementsOf(regions);
    }

    @Test
    void findProductsWithFilters_publishersFilter_addsInCriteria() {
        List<String> publishers = List.of("Capcom");

        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, publishers, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document publisherDoc = (Document) captureFindQuery().getQueryObject().get("publisher");

        assertThat(publisherDoc.getList("$in", String.class)).containsExactlyInAnyOrderElementsOf(publishers);
    }

    @Test
    void findProductsWithFilters_editionsFilter_addsInCriteria() {
        List<String> editions = List.of("Standard", "Deluxe");

        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, editions, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document editionDoc = (Document) captureFindQuery().getQueryObject().get("edition");

        assertThat(editionDoc.getList("$in", String.class)).containsExactlyInAnyOrderElementsOf(editions);
    }

    @Test
    void findProductsWithFilters_languagesFilter_addsInCriteria() {
        List<String> languages = List.of("English", "Japanese");

        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, languages, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document languagesDoc = (Document) captureFindQuery().getQueryObject().get("languages");

        assertThat(languagesDoc.getList("$in", String.class)).containsExactlyInAnyOrderElementsOf(languages);
    }

    @Test
    void findProductsWithFilters_startingLetters_buildsCharacterClassRegex() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, List.of("a", "b", "c"), null, CURRENT_DATE, VALID_PAGEABLE);

        Pattern namePattern = (Pattern) captureFindQuery().getQueryObject().get("name");

        assertThat(namePattern.pattern()).isEqualTo("^[abc]");
        assertThat(namePattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
    }

    @Test
    void findProductsWithFilters_singleReleaseDate_addsMonthRangeCriteria() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, null,
                List.of(YearMonth.of(2026, Month.MARCH)), CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        List<Document> orClauses = queryObject.getList("$or", Document.class);

        assertThat(orClauses).hasSize(1);
        Document releaseDateDoc = (Document) orClauses.get(0).get("release_date");
        assertThat(releaseDateDoc)
                .containsEntry("$gte", "2026-03-01")
                .containsEntry("$lte", "2026-03-31");
    }

    @Test
    void findProductsWithFilters_multipleReleaseDates_orsEachMonthRange() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, null,
                List.of(YearMonth.of(2026, Month.MARCH), YearMonth.of(2026, Month.JUNE)), CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        List<Document> orClauses = queryObject.getList("$or", Document.class);

        assertThat(orClauses).hasSize(2);
    }

    @Test
    void findProductsWithFilters_availabilityInStockOnly_addsStockAndReleaseDateCriteria() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, List.of(IN_STOCK), null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        Document stockDoc = (Document) queryObject.get("stock");
        Document releaseDateDoc = (Document) queryObject.get("release_date");

        assertThat(stockDoc).containsEntry("$gt", 0);
        assertThat(releaseDateDoc).containsEntry("$lte", CURRENT_DATE);
    }

    @Test
    void findProductsWithFilters_availabilityOutOfStockOnly_addsStockAndReleaseDateCriteria() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, List.of(OUT_OF_STOCK), null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        Document stockDoc = (Document) queryObject.get("stock");
        Document releaseDateDoc = (Document) queryObject.get("release_date");

        assertThat(stockDoc).containsEntry("$lte", 0);
        assertThat(releaseDateDoc).containsEntry("$lte", CURRENT_DATE);
    }

    @Test
    void findProductsWithFilters_availabilityPreorderOnly_addsReleaseDateAfterCurrentDate() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, List.of(PREORDER), null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        Document releaseDateDoc = (Document) queryObject.get("release_date");

        assertThat(releaseDateDoc).containsEntry("$gt", CURRENT_DATE);
        assertThat(queryObject.containsKey("stock")).isFalse();
    }

    @Test
    void findProductsWithFilters_availabilityInStockAndOutOfStock_addsReleaseDateOnly() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, List.of(IN_STOCK, OUT_OF_STOCK), null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        Document releaseDateDoc = (Document) queryObject.get("release_date");

        assertThat(releaseDateDoc).containsEntry("$lte", CURRENT_DATE);
        assertThat(queryObject.containsKey("stock")).isFalse();
    }

    @Test
    void findProductsWithFilters_availabilityInStockAndPreorder_orsStockAndReleaseDate() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, List.of(IN_STOCK, PREORDER), null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        List<Document> orClauses = queryObject.getList("$or", Document.class);

        assertThat(orClauses).hasSize(2);
    }

    @Test
    void findProductsWithFilters_availabilityOutOfStockAndPreorder_orsStockAndReleaseDate() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, List.of(OUT_OF_STOCK, PREORDER), null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();
        List<Document> orClauses = queryObject.getList("$or", Document.class);

        assertThat(orClauses).hasSize(2);
    }

    /**
     * Documents current (likely unintended) behavior: when all three availability values are
     * supplied together, none of the {@code Set.of(...)} branches in
     * {@link CustomProductRepositoryImpl#findProductsWithFilters} match, so no availability
     * criteria is applied at all - the filter is silently dropped instead of being a no-op
     * "match everything" or throwing.
     */
    @Test
    void findProductsWithFilters_allThreeAvailabilityValues_addsNoAvailabilityCriteria() {
        repository.findProductsWithFilters(
                VALID_PLATFORM, null, List.of(IN_STOCK, OUT_OF_STOCK, PREORDER), null, null, null, null, null, null, null, null, null, CURRENT_DATE, VALID_PAGEABLE);

        Document queryObject = captureFindQuery().getQueryObject();

        assertThat(queryObject.containsKey("stock")).isFalse();
        assertThat(queryObject.containsKey("release_date")).isFalse();
        assertThat(queryObject.containsKey("$or")).isFalse();
    }

    @Test
    void findProductsWithFilters_appliesPagination() {
        Pageable pageable = PageRequest.of(2, 5);

        repository.findProductsWithFilters(
                VALID_PLATFORM, null, null, null, null, null, null, null, null, null, null, null, CURRENT_DATE, pageable);

        Query capturedQuery = captureFindQuery();

        assertThat(capturedQuery.getSkip()).isEqualTo(10);
        assertThat(capturedQuery.getLimit()).isEqualTo(5);
    }

    // ---------- searchProducts ----------

    @Test
    void searchProducts_nullSearchTerm_throws() {
        assertThatThrownBy(() -> repository.searchProducts(null, 10))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void searchProducts_blankSearchTerm_throws() {
        assertThatThrownBy(() -> repository.searchProducts("   ", 10))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void searchProducts_searchTermTooLong_throws() {
        String tooLongSearchTerm = "a".repeat(101);

        assertThatThrownBy(() -> repository.searchProducts(tooLongSearchTerm, 10))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(CANNOT_EXCEED_MSG);
    }

    @Test
    void searchProducts_searchTermWithUnsafeCharacters_throws() {
        assertThatThrownBy(() -> repository.searchProducts("mario<script>", 10))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(INVALID_CHARACTERS_MSG);
    }

    @Test
    void searchProducts_limitZeroOrNegative_throws() {
        assertThatThrownBy(() -> repository.searchProducts("mario", 0))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(LIMIT_MUST_BE_BETWEEN_MSG);

        assertThatThrownBy(() -> repository.searchProducts("mario", -1))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(LIMIT_MUST_BE_BETWEEN_MSG);
    }

    @Test
    void searchProducts_limitExceedsMax_throws() {
        assertThatThrownBy(() -> repository.searchProducts("mario", 26))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining(LIMIT_MUST_BE_BETWEEN_MSG);
    }

    @Test
    void searchProducts_validInput_buildsPrefixRegexSortedByNameWithLimit() {
        repository.searchProducts("  Mario  ", 10);

        Query capturedQuery = captureFindQuery();
        Document queryObject = capturedQuery.getQueryObject();

        Pattern namePattern = (Pattern) queryObject.get("name");
        assertThat(namePattern.pattern()).isEqualTo("^\\QMario\\E");
        assertThat(namePattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
        assertThat(capturedQuery.getLimit()).isEqualTo(10);
        assertThat(capturedQuery.getSortObject()).containsEntry("name", 1);
    }
}
