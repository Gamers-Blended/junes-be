package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.model.CartItem;
import com.gamersblended.junes.model.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartProductMapper {

    CartItemDTO toDTO(CartItem cartItem);

    List<CartItemDTO> toDTOList(List<CartItem> cartItemList);

    Product toEntity(CartItemDTO dto);

}
