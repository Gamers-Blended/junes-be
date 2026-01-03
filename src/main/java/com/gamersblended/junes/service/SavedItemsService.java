package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.mapper.AddressMapper;
import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.repository.jpa.AddressRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class SavedItemsService {

    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;

    public SavedItemsService(AddressRepository addressRepository, AddressMapper addressMapper) {
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
    }

    public List<AddressDTO> getAllSavedAddressesForUser(UUID userID) {
        List<Address> adressesFromUserList = addressRepository.getTop5AddressesByUserID(userID);

        if (adressesFromUserList.size() == 5) {
            log.info("User {} has reached the maximum of 5 saved addresses", userID);
        }

        return addressMapper.toDTOList(adressesFromUserList);
    }
}
