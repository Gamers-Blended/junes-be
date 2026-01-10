package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    @Query(value = "SELECT * FROM junes_rel.addresses WHERE user_id = :userID AND deleted_on IS NULL ORDER BY created_on DESC LIMIT 5", nativeQuery = true)
    List<Address> getTop5AddressesByUserID(@Param("userID") UUID userID);

    @Query(value = "SELECT * FROM junes_rel.addresses WHERE user_id = :userID AND address_id = :addressID AND deleted_on IS NULL", nativeQuery = true)
    Optional<Address> getAddressByUserIDAndID(@Param("userID") UUID userID, @Param("addressID") UUID addressID);
}
