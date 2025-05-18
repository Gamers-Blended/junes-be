package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class ProductDTO {

    private String id;
    private String name;
    private Double price;
    private String platform;
    private String region;
    private LocalDate releaseDate;
    private Set<String> series;
    private Set<String> genres;
    private Integer unitsSold;
    private String srcUrl;

    /**
     * Code can read but cannot change series set
     * @return unmodifiable view of series
     */
    public Set<String> getSeries() {
        return Collections.unmodifiableSet(series);
    }

    public Set<String> getGenres() {
        return Collections.unmodifiableSet(genres);
    }

    /**
     * Accepting a copy prevents external modification
     * @param series
     */
    public void setSeries(Set<String> series) {
        this.series = new HashSet<>(series);
    }

    public void setGenres(Set<String> genres) {
        this.genres = new HashSet<>(genres);
    }
}
