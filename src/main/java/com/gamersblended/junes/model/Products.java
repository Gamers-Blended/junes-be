package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Entity
@Document(collection = "products")
@Getter
@Setter
public class Products {

    @Id
    private String id; // Maps to _id field in MongoDB

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String region;
}
