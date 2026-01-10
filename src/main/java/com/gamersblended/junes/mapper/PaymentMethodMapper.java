package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.model.PaymentMethod;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMethodMapper {

    PaymentMethodDTO toDTO(PaymentMethod paymentMethod);

    PaymentMethod toEntity(PaymentMethodDTO paymentMethodDTO);

    List<PaymentMethodDTO> toDTOList(List<PaymentMethod> paymentMethodList);

    void updateEntityFromDTO(PaymentMethodDTO paymentMethodDTO, @MappingTarget PaymentMethod paymentMethod);

}
