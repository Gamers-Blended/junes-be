package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Getter
@Setter
public class ProductDTO {

    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String platform;
    private String region;
    private String edition;
    private String publisher;
    private LocalDate releaseDate;
    private Set<String> series;
    private Set<String> genres;
    private Set<String> languages;
    private Set<String> numberOfPlayers;
    private Integer unitsSold;
    private Integer stock;
    private String productImageUrl;
    private List<String> imageUrlList;
    private String editionNotes;
    private LocalDate createdOn;

    /**
     * Code can read but cannot change set
     *
     * @return Unmodifiable view of series
     */
    public Set<String> getSeries() {
        return Collections.unmodifiableSet(series);
    }

    public Set<String> getGenres() {
        return Collections.unmodifiableSet(genres);
    }

    public Set<String> getLanguages() {
        return Collections.unmodifiableSet(languages);
    }

    public Set<String> getNumberOfPlayers() {
        return Collections.unmodifiableSet(numberOfPlayers);
    }

    public List<String> getImageUrlList() {
        return List.copyOf(imageUrlList);
    }

    // Accepting a copy prevents external modification
    public void setSeries(Set<String> series) {
        this.series = new HashSet<>(series);
    }

    public void setGenres(Set<String> genres) {
        this.genres = new HashSet<>(genres);
    }

    public void setLanguages(Set<String> languages) {
        this.languages = new HashSet<>(languages);
    }

    public void setNumberOfPlayers(Set<String> numberOfPlayers) {
        this.numberOfPlayers = new HashSet<>(numberOfPlayers);
    }

    public void setImageUrlList(List<String> imageUrlList) {
        this.imageUrlList = new ArrayList<>(imageUrlList);
    }
}
