package com.gamersblended.junes.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    private final MongoConfig mongoConfig = new MongoConfig();

    @Test
    void mongoCustomConversions_returnsNonNullConversions() {
        MongoCustomConversions conversions = mongoConfig.mongoCustomConversions();

        assertThat(conversions).isNotNull();
    }

    @Test
    void localDateToDateConverter_convertsToStartOfDayUtc() {
        MongoConfig.LocalDateToDateConverter converter = new MongoConfig.LocalDateToDateConverter();
        LocalDate source = LocalDate.of(2026, Month.MARCH, 15);

        Date result = converter.convert(source);

        assertThat(result).isEqualTo(Date.from(source.atStartOfDay(ZoneOffset.UTC).toInstant()));
    }

    @Test
    void dateToLocalDateConverter_convertsBackToOriginalLocalDate() {
        MongoConfig.DateToLocalDateConverter converter = new MongoConfig.DateToLocalDateConverter();
        LocalDate original = LocalDate.of(2026, Month.MARCH, 15);
        Date source = Date.from(original.atStartOfDay(ZoneOffset.UTC).toInstant());

        LocalDate result = converter.convert(source);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void converters_roundTrip_preservesLocalDate() {
        MongoConfig.LocalDateToDateConverter toDate = new MongoConfig.LocalDateToDateConverter();
        MongoConfig.DateToLocalDateConverter toLocalDate = new MongoConfig.DateToLocalDateConverter();
        LocalDate original = LocalDate.of(1999, Month.DECEMBER, 31);

        LocalDate roundTripped = toLocalDate.convert(Objects.requireNonNull(toDate.convert(original)));

        assertThat(roundTripped).isEqualTo(original);
    }
}
