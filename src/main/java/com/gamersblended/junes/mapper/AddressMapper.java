package com.gamersblended.junes.mapper;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.model.Address;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    AddressDTO toDTO(Address address);

    List<AddressDTO> toDTOList(List<Address> addressList);

}
