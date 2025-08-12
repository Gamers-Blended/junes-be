package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.constant.PlatformEnums;
import com.gamersblended.junes.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class CustomProductRepositoryImpl implements CustomProductRepository {
    private static final int MAX_STRING_LENGTH = 100;
    private static final int MAX_LIST_SIZE = 20;
    private static final int PAGE_SIZE_LIMIT = 100;
    private static final String IN_STOCK = "in_stock";
    private static final String OUT_OF_STOCK = "out_of_stock";
    private static final String PREORDER = "preorder";
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_.,!?'\"()&]+$");

    private MongoTemplate mongoTemplate;

    public CustomProductRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(mongoTemplate, "MongoTemplate cannot be null");
    }

    @Override
    public Page<Product> findProductsWithFilters(
            String platform,
            String name,
            List<String> availability,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> genres,
            List<String> regions,
            List<String> publishers,
            List<String> editions,
            List<String> languages,
            List<String> startingLetters,
            List<YearMonth> releaseDates,
            String currentDate,
            Pageable pageable) {

        validateInputs(platform, name,
                minPrice, maxPrice, genres,
                regions, publishers, editions,
                languages, startingLetters,
                pageable);

        Query query = new Query();

        // Platform (required)
        query.addCriteria(Criteria.where("platform").is(platform));

        // Name like '%name%'
        if (null != name && !name.isEmpty()) {
            query.addCriteria(Criteria.where("name").regex(".*" + name.trim() + ".*", "i"));
        }

        // Price range
        if (null != minPrice && null != maxPrice) {
            log.info("Only products priced between {} and {} (inclusive) will be returned", minPrice, maxPrice);
            query.addCriteria(Criteria.where("price").gte(minPrice.doubleValue()).lte(maxPrice.doubleValue()));
        } else if (null != minPrice) {
            log.info("Only products priced at least {} will be returned", minPrice);
            query.addCriteria(Criteria.where("price").gte(minPrice.doubleValue()));
        } else if (null != maxPrice) {
            log.info("Only products priced at most {} will be returned", maxPrice);
            query.addCriteria(Criteria.where("price").lte(maxPrice.doubleValue()));
        }

        // Lists (IN operator)
        if (null != genres && !genres.isEmpty()) {
            log.info("Only products with this/these genre(s) = {} will be returned", genres);
            query.addCriteria(Criteria.where("genres").in(genres));
        }

        if (null != regions && !regions.isEmpty()) {
            log.info("Only products from this/these region(s) = {} will be returned", regions);
            query.addCriteria(Criteria.where("region").in(regions));
        }

        if (null != publishers && !publishers.isEmpty()) {
            log.info("Only products by this/these publisher(s) = {} will be returned", publishers);
            query.addCriteria(Criteria.where("publisher").in(publishers));
        }

        if (null != editions && !editions.isEmpty()) {
            log.info("Only products under this/these edition(s) = {} will be returned", editions);
            query.addCriteria(Criteria.where("edition").in(editions));
        }

        if (null != languages && !languages.isEmpty()) {
            log.info("Only products supporting this/these language(s) = {} will be returned", languages);
            query.addCriteria(Criteria.where("languages").in(languages));
        }

        if (null != startingLetters && !startingLetters.isEmpty()) {
            log.info("Only products starting with letter(s) = {} will be returned", startingLetters);

            // Create a character class like "^[abc]" for letters a, b, c
            String regexPattern = "^[" + String.join("", startingLetters) + "]";
            query.addCriteria(Criteria.where("name").regex(regexPattern, "i"));
        }

        // Release date (month and year match)
        if (null != releaseDates && !releaseDates.isEmpty()) {

            // Log all date ranges
            List<String> dateRanges = releaseDates.stream()
                    .map(releaseDate -> {
                        String startDate = releaseDate.atDay(1).toString();
                        String endDate = releaseDate.atEndOfMonth().toString();
                        return startDate + " to " + endDate;
                    })
                    .collect(Collectors.toList());

            log.info("Only products that were released in any of these date ranges: {} (inclusive) will be returned", dateRanges);

            Criteria[] dateCriteria = releaseDates.stream()
                    .map(this::createReleaseDateCriteria)
                    .toArray(Criteria[]::new);

            query.addCriteria(new Criteria().orOperator(dateCriteria));
        }

        // Availability (in_stock, out_of_stock, preorder)
        if (null != availability && !availability.isEmpty()) {
            Set<String> availabilitySet = new HashSet<>(availability);

            if (availabilitySet.equals(Set.of(IN_STOCK))) {
                // stock > 0 & release_date before or on currentDate
                log.info("Only products that are in stock will be returned");
                query.addCriteria(Criteria.where("stock").gt(0));
                query.addCriteria(Criteria.where("release_date").lte(currentDate));
            } else if (availabilitySet.equals(Set.of(OUT_OF_STOCK))) {
                // stock <= 0 & release_date before or on currentDate
                log.info("Only products that are out of stock will be returned");
                query.addCriteria(Criteria.where("stock").lte(0));
                query.addCriteria(Criteria.where("release_date").lte(currentDate));
            } else if (availabilitySet.equals(Set.of(PREORDER))) {
                // release_date after currentDate
                log.info("Only products that are preorders will be returned");
                query.addCriteria(Criteria.where("release_date").gt(currentDate));
            } else if (availabilitySet.equals(Set.of(IN_STOCK, OUT_OF_STOCK))) {
                // release_date before or on currentDate
                log.info("Only products that are in stock and out of stock will be returned");
                query.addCriteria(Criteria.where("release_date").lte(currentDate));
            } else if (availabilitySet.equals(Set.of(IN_STOCK, PREORDER))) {
                // stock > 0 or release_date after currentDate
                log.info("Only products that are in stock and preorders will be returned");
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("stock").gt(0),
                        Criteria.where("release_date").gt(currentDate)
                ));
            } else if (availabilitySet.equals(Set.of(OUT_OF_STOCK, PREORDER))) {
                // stock <= 0 or release_date after currentDate
                log.info("Only products that are out of stock and preorders will be returned");
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("stock").lte(0),
                        Criteria.where("release_date").gt(currentDate)
                ));
            }
        }

        // Add pagination
        query.with(pageable);

        // Execute query
        List<Product> products = mongoTemplate.find(query, Product.class);
        long count = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Product.class);

        return PageableExecutionUtils.getPage(products, pageable, () -> count);
    }

    /**
     * Create a Criteria for filtering release date to be in the same month and year
     *
     * @param releaseDate target month and year of release date
     * @return Criteria for releaseDate
     */
    private Criteria createReleaseDateCriteria(YearMonth releaseDate) {
        String startDate = releaseDate.atDay(1).toString(); // 1st day of month
        String endDate = releaseDate.atEndOfMonth().toString(); // last day of month

        // From 1st day of month till last day of month
        return Criteria.where("release_date").gte(startDate).lte(endDate);
    }

    private void validateInputs(String platform, String name,
                                BigDecimal minPrice, BigDecimal maxPrice, List<String> genres,
                                List<String> regions, List<String> publishers, List<String> editions,
                                List<String> languages, List<String> startingLetters,
                                Pageable pageable) {

        // Required field validation
        validatePlatform(platform);

        // Pageable validation
        if (null == pageable) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }

        // Page size limits
        if (pageable.getPageSize() > PAGE_SIZE_LIMIT) {
            throw new IllegalArgumentException("Page size cannot exceed " + PAGE_SIZE_LIMIT + ", given page size: " + pageable.getPageSize());
        }

        // Optional string validations - only validate if provided
        if (null != name) {
            validateStringLength("name", name);
            validateStringContent("name", name);
        }

        // Optional price validations
        if (null != minPrice) {
            validatePrice("minPrice", minPrice);
        }
        if (null != maxPrice) {
            validatePrice("maxPrice", maxPrice);
        }
        if (null != minPrice && null != maxPrice) {
            validateMinMaxPrices(minPrice, maxPrice);
        }

        // Optional list validations
        if (null != genres) {
            validateStringList("genres", genres);
        }
        if (null != regions) {
            validateStringList("regions", regions);
        }
        if (null != publishers) {
            validateStringList("publishers", publishers);
        }
        if (null != editions) {
            validateStringList("editions", editions);
        }
        if (null != languages) {
            validateStringList("languages", languages);
        }
        if (null != startingLetters) {
            for (String currentStartingLetter : startingLetters) {
                validateStartingLetter(currentStartingLetter);
            }
        }
    }

    /**
     * Checks if platform input value is non-null and is a valid value
     *
     * @param platform filter input
     */
    private void validatePlatform(String platform) {
        if (null == platform || platform.trim().isEmpty()) {
            throw new IllegalArgumentException("Platform cannot be null or empty");
        }
        if (!PlatformEnums.isValidPlatformValue(platform)) {
            throw new IllegalArgumentException("Platform is not valid! Platform: " + platform);
        }
    }

    /**
     * Checks if input string is within length limit
     *
     * @param fieldName field which string input belongs to
     * @param value     value to check
     */
    private void validateStringLength(String fieldName, String value) {
        if (null != value && value.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + MAX_STRING_LENGTH + " characters");
        }
    }

    /**
     * Checks for injections inside input string
     *
     * @param fieldName field which string input belongs to
     * @param value     value to check
     */
    private void validateStringContent(String fieldName, String value) {
        if (value != null && !SAFE_STRING_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters, given value: " + value);
        }
    }

    /**
     * Checks if string input is only 1-letter long
     *
     * @param value value to check
     */
    private void validateStartingLetter(String value) {
        if (!value.trim().isEmpty() && value.trim().length() != 1) {
            throw new IllegalArgumentException("Starting letter must be exactly one character, given starting letter: " + value);
        }
    }

    /**
     * Checks if price field is within 0 and 999999.99 + contains only 2 decimal points
     *
     * @param fieldName field which bigDecimal input belongs to
     * @param price     value to check
     */
    private void validatePrice(String fieldName, BigDecimal price) {
        if (null != price) {
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative, given value: " + price);
            }
            if (price.compareTo(new BigDecimal("999999.99")) > 0) {
                throw new IllegalArgumentException(fieldName + " cannot exceed 999999.99, given value: " + price);
            }
            if (price.scale() > 2) {
                throw new IllegalArgumentException(fieldName + " cannot have more than 2 decimal places, given value: " + price);
            }
        }
    }

    /**
     * Checks if minPrice is lower than or equal to maxPrice
     *
     * @param minPrice lower price bound
     * @param maxPrice upper price bound
     */
    private void validateMinMaxPrices(BigDecimal minPrice, BigDecimal maxPrice) {
        if (null != minPrice && null != maxPrice && minPrice.compareTo(maxPrice) > 0) {
            log.error("minPrice of {} cannot be greater than maxPrice of {}", minPrice, maxPrice);
            throw new IllegalArgumentException("minPrice of " + minPrice + " cannot be greater than maxPrice of " + maxPrice);
        }
    }

    /**
     * Checks if input list is within size limit and does not contain null values
     * Elements are within length limit and does not contain invalid characters
     *
     * @param fieldName
     * @param list
     */
    private void validateStringList(String fieldName, List<String> list) {
        if (list.size() > MAX_LIST_SIZE) {
            throw new IllegalArgumentException(fieldName + " list cannot exceed " + MAX_LIST_SIZE + " items");
        }

        for (String item : list) {
            if (null == item) {
                throw new IllegalArgumentException(fieldName + " list cannot contain null values");
            }
            validateStringLength(fieldName + " item", item);
            validateStringContent(fieldName + " item", item);
        }
    }
}
