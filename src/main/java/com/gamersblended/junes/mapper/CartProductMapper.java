package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.CartProductDTO;
import com.gamersblended.junes.model.Cart;
import com.gamersblended.junes.model.Product;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartProductMapper {

    CartProductDTO toDTO(Cart cart);

    List<CartProductDTO> toDTOList(List<Cart> cartList);

    Product toEntity(CartProductDTO dto);

}
