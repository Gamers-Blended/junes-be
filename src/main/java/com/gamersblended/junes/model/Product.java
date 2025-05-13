package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.util.List;

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
    private List<String> series;

    @NonNull
    private List<String> genres;

    @NonNull
    @Field("units_sold")
    private Integer unitsSold;

    @NonNull
    @Field("src_url")
    private String srcUrl;
}
