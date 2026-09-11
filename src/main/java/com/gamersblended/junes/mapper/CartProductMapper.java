package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.model.CartItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CartProductMapper {

    CartItemDTO toDTO(CartItem cartItem);

    CartItem toCartItemEntity(CartItemDTO dto);

}
