package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.model.Product;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

@Repository
public class CustomProductRepositoryImpl implements CustomProductRepository {
    private MongoTemplate mongoTemplate;

    public CustomProductRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    ;

    @Override
    public Page<Product> findProductsWithFilters(
            String platform,
            String name,
            List<String> availability,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> genre,
            List<String> region,
            List<String> publisher,
            List<String> edition,
            List<String> rating,
            List<String> language,
            String startingLetter,
            YearMonth releaseDate,
            Pageable pageable) {

        Query query = new Query();

        // Platform (required)
        query.addCriteria(Criteria.where("platform").is(platform));

        // Name like '%name%'
        if (name != null && !name.isEmpty()) {
            query.addCriteria(Criteria.where("name").regex(".*" + name + ".*", "i"));
        }

        // Starting letter like 'startingLetter%'
        if (startingLetter != null && !startingLetter.isEmpty()) {
            query.addCriteria(Criteria.where("name").regex("^" + startingLetter, "i"));
        }

        // Price range
        if (minPrice != null) {
            query.addCriteria(Criteria.where("price").gte(minPrice));
        }
        if (maxPrice != null) {
            query.addCriteria(Criteria.where("price").lte(maxPrice));
        }

        // Lists (IN operator)
        if (availability != null && !availability.isEmpty()) {
            query.addCriteria(Criteria.where("availability").in(availability));
        }

        if (genre != null && !genre.isEmpty()) {
            query.addCriteria(Criteria.where("genre").in(genre));
        }

        if (region != null && !region.isEmpty()) {
            query.addCriteria(Criteria.where("region").in(region));
        }

        if (publisher != null && !publisher.isEmpty()) {
            query.addCriteria(Criteria.where("publisher").in(publisher));
        }

        if (edition != null && !edition.isEmpty()) {
            query.addCriteria(Criteria.where("edition").in(edition));
        }

        if (rating != null && !rating.isEmpty()) {
            query.addCriteria(Criteria.where("rating").in(rating));
        }

        if (language != null && !language.isEmpty()) {
            query.addCriteria(Criteria.where("language").is(language));
        }

        // Release date (month and year match)
        if (releaseDate != null) {
            query.addCriteria(new Criteria().andOperator(
                    Criteria.where("releaseDate").exists(true),
                    new Criteria() {
                        @Override
                        public Document getCriteriaObject() {
                            return new Document("$expr", new Document("$and", Arrays.asList(
                                    new Document("$eq", Arrays.asList(
                                            new Document("$month", "$releaseDate"),
                                            releaseDate.getMonthValue())),
                                    new Document("$eq", Arrays.asList(
                                            new Document("$year", "$releaseDate"),
                                            releaseDate.getYear()))
                            )));
                        }
                    }
            ));
        }

        // Add pagination
        query.with(pageable);

        // Execute query
        List<Product> products = mongoTemplate.find(query, Product.class);
        long count = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Product.class);

        return PageableExecutionUtils.getPage(products, pageable, () -> count);
    }
}
