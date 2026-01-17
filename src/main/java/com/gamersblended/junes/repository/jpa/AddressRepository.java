package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    @Query(value = "SELECT * FROM junes_rel.addresses WHERE user_id = :userID AND deleted_on IS NULL ORDER BY created_on DESC LIMIT 5", nativeQuery = true)
    List<Address> getAddressesByUserID(@Param("userID") UUID userID);

    @Query(value = "SELECT * FROM junes_rel.addresses WHERE user_id = :userID AND address_id = :addressID AND deleted_on IS NULL", nativeQuery = true)
    Optional<Address> getAddressByUserIDAndID(@Param("userID") UUID userID, @Param("addressID") UUID addressID);

    @Modifying
    @Query(value = "UPDATE junes_rel.addresses SET is_default = false WHERE user_id = :userID AND is_default = true", nativeQuery = true)
    void unsetDefaultForUser(@Param("userID") UUID userID);
}
