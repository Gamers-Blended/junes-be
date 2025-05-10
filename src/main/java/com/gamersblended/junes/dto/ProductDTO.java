package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class ProductDTO {

    private String id;
    private String name;
    private Double price;
    private String platform;
    private String region;
    private LocalDate releaseDate;
    private List<String> series;
    private List<String> genres;
    private Integer unitsSold;
}
