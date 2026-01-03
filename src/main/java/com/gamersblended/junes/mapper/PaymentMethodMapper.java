package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.model.PaymentMethod;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMethodMapper {

    PaymentMethodDTO toDTO(PaymentMethod paymentMethod);

    List<PaymentMethodDTO> toDTOList(List<PaymentMethod> paymentMethodList);

}
