package com.gamersblended.junes.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private Product validProduct(Set<String> series, List<String> imageUrlList) {
        return new Product(
                "Elden Ring",
                "elden-ring",
                "An action RPG",
                new BigDecimal("59.99"),
                "PS5",
                "US",
                "Standard",
                "Bandai Namco",
                LocalDate.of(2022, Month.FEBRUARY, 25),
                series,
                new HashSet<>(Set.of("Action RPG")),
                new HashSet<>(Set.of("English")),
                new HashSet<>(Set.of("1")),
                new BigDecimal("0.10"),
                20_000_000,
                100,
                "https://example.com/image.png",
                imageUrlList,
                LocalDate.of(2025, Month.JANUARY, 1)
        );
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void constructor_throwsNullPointerException_whenNameIsNull() {
        BigDecimal price = new BigDecimal("59.99");
        LocalDate releaseDate = LocalDate.of(2022, Month.FEBRUARY, 25);
        Set<String> series = new HashSet<>(Set.of("Souls"));
        Set<String> genres = new HashSet<>(Set.of("Action RPG"));
        Set<String> languages = new HashSet<>(Set.of("English"));
        Set<String> numberOfPlayers = new HashSet<>(Set.of("1"));
        BigDecimal weight = new BigDecimal("0.10");
        List<String> imageUrlList = List.of("https://example.com/image.png");
        LocalDate createdOn = LocalDate.of(2025, Month.JANUARY, 1);

        assertThatThrownBy(() -> new Product(
                null,
                "elden-ring",
                "An action RPG",
                price,
                "PS5",
                "US",
                "Standard",
                "Bandai Namco",
                releaseDate,
                series,
                genres,
                languages,
                numberOfPlayers,
                weight,
                20_000_000,
                100,
                "https://example.com/image.png",
                imageUrlList,
                createdOn
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("Name cannot be null");
    }

    @Test
    void constructor_defensivelyCopiesSeries() {
        Set<String> series = new HashSet<>(Set.of("Souls"));
        Product product = validProduct(series, List.of("https://example.com/image.png"));

        series.add("Dark Souls");

        assertThat(product.getSeries()).containsExactly("Souls");
    }

    @Test
    void constructor_defensivelyCopiesImageUrlList() {
        List<String> imageUrlList = new ArrayList<>(List.of("https://example.com/1.png"));
        Product product = validProduct(new HashSet<>(Set.of("Souls")), imageUrlList);

        imageUrlList.add("https://example.com/2.png");

        assertThat(product.getImageUrlList()).containsExactly("https://example.com/1.png");
    }

    @Test
    void getSeries_returnsImmutableSet() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));

        Set<String> series = product.getSeries();

        assertThatThrownBy(() -> series.add("Dark Souls"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getImageUrlList_returnsImmutableList() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));

        List<String> imageUrlList = product.getImageUrlList();

        assertThatThrownBy(() -> imageUrlList.add("https://example.com/2.png"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setSeries_defensivelyCopiesInput() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));
        Set<String> newSeries = new HashSet<>(Set.of("Bloodborne"));

        product.setSeries(newSeries);
        newSeries.add("Sekiro");

        assertThat(product.getSeries()).containsExactly("Bloodborne");
    }

    @Test
    void setImageUrlList_defensivelyCopiesInput() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));
        List<String> newImageUrlList = new ArrayList<>(List.of("https://example.com/new.png"));

        product.setImageUrlList(newImageUrlList);
        newImageUrlList.add("https://example.com/extra.png");

        assertThat(product.getImageUrlList()).containsExactly("https://example.com/new.png");
    }

    @Test
    void getGenres_returnsImmutableSet() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));

        Set<String> genres = product.getGenres();

        assertThatThrownBy(() -> genres.add("Metroidvania"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setGenres_defensivelyCopiesInput() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));
        Set<String> newGenres = new HashSet<>(Set.of("Metroidvania"));

        product.setGenres(newGenres);
        newGenres.add("Platformer");

        assertThat(product.getGenres()).containsExactly("Metroidvania");
    }

    @Test
    void getLanguages_returnsImmutableSet() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));

        Set<String> languages = product.getLanguages();

        assertThatThrownBy(() -> languages.add("French"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setLanguages_defensivelyCopiesInput() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));
        Set<String> newLanguages = new HashSet<>(Set.of("French"));

        product.setLanguages(newLanguages);
        newLanguages.add("German");

        assertThat(product.getLanguages()).containsExactly("French");
    }

    @Test
    void getNumberOfPlayers_returnsImmutableSet() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));

        Set<String> numberOfPlayers = product.getNumberOfPlayers();

        assertThatThrownBy(() -> numberOfPlayers.add("2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setNumberOfPlayers_defensivelyCopiesInput() {
        Product product = validProduct(new HashSet<>(Set.of("Souls")), List.of("https://example.com/image.png"));
        Set<String> newNumberOfPlayers = new HashSet<>(Set.of("2"));

        product.setNumberOfPlayers(newNumberOfPlayers);
        newNumberOfPlayers.add("4");

        assertThat(product.getNumberOfPlayers()).containsExactly("2");
    }
}
