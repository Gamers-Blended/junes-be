package com.gamersblended.junes.model;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Document(collection = "products")
@Getter
@Setter
public class Product {

    @Id
    private String id; // Maps to _id field in MongoDB

    @NonNull
    private String name;

    @NonNull
    private Double price;

    @NonNull
    private String platform;

    @NonNull
    private String region;

    @NonNull
    @Field("release_date")
    private LocalDate releaseDate;

    @NonNull
    private Set<String> series;

    @NonNull
    private Set<String> genres;

    @NonNull
    @Field("units_sold")
    private Integer unitsSold;

    @NonNull
    @Field("src_url")
    private String srcUrl;

    /**
     * Private constructor for frameworks like Spring/MongoDB/JPA
     */
    private Product() {
        // Initialize fields to prevent null values
        this.name = "";
        this.price = 0.0;
        this.platform = "";
        this.region = "";
        this.releaseDate = LocalDate.parse("2025-01-01");
        this.series = new HashSet<>();
        this.genres = new HashSet<>();
        this.unitsSold = 0;
        this.srcUrl = "";
    }

    /**
     * All-args constructor ensures that Product won't be partially initialized
     */
    public Product(@NonNull String name, @NonNull Double price, @NonNull String platform,
                   @NonNull String region, @NonNull LocalDate releaseDate, @NonNull List<String> series,
                   @NonNull List<String> genres, @NonNull Integer unitsSold, @NonNull String srcUrl) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(platform, "Platform cannot be null");
        Objects.requireNonNull(region, "Region cannot be null");
        Objects.requireNonNull(releaseDate, "Release date cannot be null");
        Objects.requireNonNull(series, "Series cannot be null");
        Objects.requireNonNull(genres, "Genres cannot be null");
        Objects.requireNonNull(unitsSold, "Units sold cannot be null");
        Objects.requireNonNull(srcUrl, "Source URL cannot be null");

        this.name = name;
        this.price = price;
        this.platform = platform;
        this.region = region;
        this.releaseDate = releaseDate;
        this.series = Set.copyOf(series);
        this.genres = Set.copyOf(genres);
        this.unitsSold = unitsSold;
        this.srcUrl = srcUrl;
    }

    public Set<String> getSeries() {
        return Set.copyOf(series);
    }

    public Set<String> getGenres() {
        return Set.copyOf(genres);
    }

    public void setSeries(List<String> series) {
        this.series = Set.copyOf(series);
    }

    public void setGenres(List<String> genres) {
        this.genres = Set.copyOf(genres);
    }
}
