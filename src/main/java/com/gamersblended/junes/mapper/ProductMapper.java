package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.model.Product;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", source = "id")
    ProductDTO toDTO(Product product);

    default String map(ObjectId value) {
        return value != null ? value.toHexString() : null;
    }

    @Mapping(source = "id", target = "productID")
    ProductSliderItemDTO toSliderItemDTO(Product product);
}
