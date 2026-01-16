package com.gamersblended.junes.dto.request;

import com.gamersblended.junes.dto.TransactionItemDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CalculateShippingRequest {

    private List<TransactionItemDTO> transactionItemDTOList;
}
