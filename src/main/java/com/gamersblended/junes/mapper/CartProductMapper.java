package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.model.CartItems;
import com.gamersblended.junes.model.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartProductMapper {

    CartItemDTO toDTO(CartItems cartItems);

    List<CartItemDTO> toDTOList(List<CartItems> cartItemsList);

    Product toEntity(CartItemDTO dto);

}
