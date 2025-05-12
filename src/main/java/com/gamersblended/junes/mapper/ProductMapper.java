package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.model.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDTO toDTO(Product product);

    List<ProductDTO> toDTOList(List<Product> products);

    Product toEntity(ProductDTO dto);

}
