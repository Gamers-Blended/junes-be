package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.model.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDTO toDTO(Product product);

    List<ProductDTO> toDTOList(List<Product> productList);

    Product toEntity(ProductDTO dto);

    ProductSliderItemDTO toSliderItemDTO(Product product);

    List<ProductSliderItemDTO> toSliderItemDTOList(List<Product> productList);
}
