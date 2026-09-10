package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartDatabaseRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserID(UUID userID);

    @Modifying
    @Query(value = "DELETE FROM junes_rel.carts WHERE updated_on < :cutoffDate", nativeQuery = true)
    int deleteInactiveCarts(@Param("cutoffDate") LocalDateTime cutoffDate);

}
