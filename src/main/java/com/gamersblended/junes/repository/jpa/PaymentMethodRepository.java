package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Address;
import com.gamersblended.junes.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    @Query(value = "SELECT * FROM junes_rel.payment_methods WHERE user_id = :userID ORDER BY created_on DESC LIMIT 5", nativeQuery = true)
    List<PaymentMethod> getTop5PaymentMethodsByUserID(@Param("userID") UUID userID);
}
