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
import java.util.ArrayList;
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
    private String description;

    @NonNull
    private Double price;

    @NonNull
    private String platform;

    @NonNull
    private String region;

    @NonNull
    private String edition;

    @NonNull
    @Field("release_date")
    private LocalDate releaseDate;

    @NonNull
    private Set<String> series;

    @NonNull
    private Set<String> genres;

    @NonNull
    private Set<String> languages;

    @NonNull
    @Field("number_of_players")
    private Set<String> numberOfPlayers;

    @NonNull
    @Field("units_sold")
    private Integer unitsSold;

    @NonNull
    @Field("product_image_url")
    private String productImageUrl;

    @NonNull
    @Field("image_url_list")
    private List<String> imageUrlList;

    @Field("created_on")
    private LocalDate createdOn;

    @Field("updated_on")
    private LocalDate updatedOn;

    /**
     * Private constructor for frameworks like Spring/MongoDB/JPA
     */
    private Product() {
        // Initialize fields to prevent null values
        this.name = "";
        this.price = 0.0;
        this.description = "";
        this.platform = "";
        this.region = "";
        this.edition = "";
        this.releaseDate = LocalDate.parse("2025-01-01");
        this.series = new HashSet<>();
        this.genres = new HashSet<>();
        this.languages = new HashSet<>();
        this.numberOfPlayers = new HashSet<>();
        this.unitsSold = 0;
        this.productImageUrl = "";
        this.imageUrlList = new ArrayList<>();
        this.createdOn = LocalDate.parse("2025-01-01");
    }

    /**
     * All-args constructor ensures that Product won't be partially initialized
     */
    public Product(@NonNull String name,
                   @NonNull String description,
                   @NonNull Double price,
                   @NonNull String platform,
                   @NonNull String region,
                   @NonNull String edition,
                   @NonNull LocalDate releaseDate,
                   @NonNull Set<String> series,
                   @NonNull Set<String> genres,
                   @NonNull Set<String> languages,
                   @NonNull Set<String> numberOfPlayers,
                   @NonNull Integer unitsSold,
                   @NonNull String productImageUrl,
                   @NonNull List<String> imageUrlList,
                   @NonNull LocalDate createdOn,
                   LocalDate updatedOn) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(description, "Description cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(platform, "Platform cannot be null");
        Objects.requireNonNull(region, "Region cannot be null");
        Objects.requireNonNull(edition, "Edition cannot be null");
        Objects.requireNonNull(releaseDate, "Release date cannot be null");
        Objects.requireNonNull(series, "Series cannot be null");
        Objects.requireNonNull(genres, "Genres cannot be null");
        Objects.requireNonNull(languages, "Languages cannot be null");
        Objects.requireNonNull(numberOfPlayers, "Number of players cannot be null");
        Objects.requireNonNull(unitsSold, "Units sold cannot be null");
        Objects.requireNonNull(productImageUrl, "Source URL cannot be null");
        Objects.requireNonNull(imageUrlList, "Image URL list cannot be null");
        Objects.requireNonNull(createdOn, "Created on date cannot be null");

        this.name = name;
        this.description = description;
        this.price = price;
        this.platform = platform;
        this.region = region;
        this.edition = edition;
        this.releaseDate = releaseDate;
        this.series = Set.copyOf(series);
        this.genres = Set.copyOf(genres);
        this.languages = Set.copyOf(languages);
        this.numberOfPlayers = Set.copyOf(numberOfPlayers);
        this.unitsSold = unitsSold;
        this.productImageUrl = productImageUrl;
        this.imageUrlList = List.copyOf(imageUrlList);
        this.createdOn = createdOn;
    }

    public Set<String> getSeries() {
        return Set.copyOf(series);
    }

    public Set<String> getGenres() {
        return Set.copyOf(genres);
    }

    public Set<String> getLanguages() {
        return Set.copyOf(languages);
    }

    public Set<String> getNumberOfPlayers() {
        return Set.copyOf(numberOfPlayers);
    }

    public List<String> getImageUrlList() {
        return List.copyOf(imageUrlList);
    }

    public void setSeries(Set<String> series) {
        this.series = Set.copyOf(series);
    }

    public void setGenres(Set<String> genres) {
        this.genres = Set.copyOf(genres);
    }

    public void setLanguages(Set<String> languages) {
        this.languages = Set.copyOf(languages);
    }

    public void setNumberOfPlayers(Set<String> numberOfPlayers) {
        this.numberOfPlayers = Set.copyOf(numberOfPlayers);
    }

    public void setImageUrlList(List<String> imageUrlList) {
        this.imageUrlList = List.copyOf(imageUrlList);
    }
}
