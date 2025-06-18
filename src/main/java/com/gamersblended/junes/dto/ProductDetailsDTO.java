package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductDetailsDTO {

    private ProductDTO productDTO;
    private List<ProductVariantDTO> productVariantDTOList;
}
