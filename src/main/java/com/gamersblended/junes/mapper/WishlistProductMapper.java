package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.WishlistItemDTO;
import com.gamersblended.junes.model.WishlistItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WishlistProductMapper {

    WishlistItem toWishlistItemEntity(WishlistItemDTO dto);

}
