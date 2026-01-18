package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.TransactionItemDTO;
import com.gamersblended.junes.model.TransactionItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TransactionItemMapper {
    TransactionItemDTO toDTO(TransactionItem transactionItem);

    List<TransactionItemDTO> toDTOList(List<TransactionItem> productList);

    TransactionItem toEntity(TransactionItemDTO dto);

    List<TransactionItem> toEntityList(List<TransactionItemDTO> dtoList);

}
