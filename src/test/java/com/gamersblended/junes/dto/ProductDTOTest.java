package com.gamersblended.junes.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDTOTest {

    @Test
    void setSeries_storesDefensiveCopy_soMutatingOriginalSetAfterwardsDoesNotAffectDto() {
        ProductDTO dto = new ProductDTO();
        Set<String> original = new HashSet<>(Set.of("Souls"));

        dto.setSeries(original);
        original.add("Elden Ring");

        assertThat(dto.getSeries()).containsExactly("Souls");
    }

    @Test
    void getSeries_returnsUnmodifiableView_thatThrowsOnMutation() {
        ProductDTO dto = new ProductDTO();
        dto.setSeries(Set.of("Souls"));

        Set<String> series = dto.getSeries();

        assertThatThrownBy(() -> series.add("Elden Ring"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setGenres_storesDefensiveCopy_soMutatingOriginalSetAfterwardsDoesNotAffectDto() {
        ProductDTO dto = new ProductDTO();
        Set<String> original = new HashSet<>(Set.of("Action RPG"));

        dto.setGenres(original);
        original.add("Souls-like");

        assertThat(dto.getGenres()).containsExactly("Action RPG");
    }

    @Test
    void getGenres_returnsUnmodifiableView_thatThrowsOnMutation() {
        ProductDTO dto = new ProductDTO();
        dto.setGenres(Set.of("Action RPG"));

        Set<String> genres = dto.getGenres();

        assertThatThrownBy(() -> genres.add("Souls-like"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setLanguages_storesDefensiveCopy_soMutatingOriginalSetAfterwardsDoesNotAffectDto() {
        ProductDTO dto = new ProductDTO();
        Set<String> original = new HashSet<>(Set.of("English"));

        dto.setLanguages(original);
        original.add("Japanese");

        assertThat(dto.getLanguages()).containsExactly("English");
    }

    @Test
    void getLanguages_returnsUnmodifiableView_thatThrowsOnMutation() {
        ProductDTO dto = new ProductDTO();
        dto.setLanguages(Set.of("English"));

        Set<String> languages = dto.getLanguages();

        assertThatThrownBy(() -> languages.add("Japanese"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setNumberOfPlayers_storesDefensiveCopy_soMutatingOriginalSetAfterwardsDoesNotAffectDto() {
        ProductDTO dto = new ProductDTO();
        Set<String> original = new HashSet<>(Set.of("1"));

        dto.setNumberOfPlayers(original);
        original.add("2-4");

        assertThat(dto.getNumberOfPlayers()).containsExactly("1");
    }

    @Test
    void getNumberOfPlayers_returnsUnmodifiableView_thatThrowsOnMutation() {
        ProductDTO dto = new ProductDTO();
        dto.setNumberOfPlayers(Set.of("1"));

        Set<String> numberOfPlayers = dto.getNumberOfPlayers();

        assertThatThrownBy(() -> numberOfPlayers.add("2-4"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setImageUrlList_storesDefensiveCopy_soMutatingOriginalListAfterwardsDoesNotAffectDto() {
        ProductDTO dto = new ProductDTO();
        List<String> original = new ArrayList<>(List.of("https://example.com/image.png"));

        dto.setImageUrlList(original);
        original.add("https://example.com/image2.png");

        assertThat(dto.getImageUrlList()).containsExactly("https://example.com/image.png");
    }

    @Test
    void getImageUrlList_returnsUnmodifiableView_thatThrowsOnMutation() {
        ProductDTO dto = new ProductDTO();
        dto.setImageUrlList(List.of("https://example.com/image.png"));

        List<String> imageUrlList = dto.getImageUrlList();

        assertThatThrownBy(() -> imageUrlList.add("https://example.com/image2.png"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
