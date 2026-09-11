package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.TransactionItemDTO;
import com.gamersblended.junes.model.TransactionItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionItemMapper {
    TransactionItemDTO toDTO(TransactionItem transactionItem);

}
