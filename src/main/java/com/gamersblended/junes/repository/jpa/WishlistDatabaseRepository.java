package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistDatabaseRepository extends JpaRepository<Wishlist, UUID> {

    Optional<Wishlist> findByUserID(UUID userID);

    boolean existsByUserID(UUID userID);

    void deleteByUserID(UUID userID);

    // Find wishlists that haven't been updated in n days (for cleanup)
    @Query(value = "SELECT * FROM junes_rel.wishlists WHERE updated_on < :cutoffDate", nativeQuery = true)
    List<Wishlist> findInactiveWishlists(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query(value = "DELETE FROM junes_rel.wishlists WHERE updated_on < :cutoffDate", nativeQuery = true)
    int deleteInactiveWishlists(@Param("cutoffDate") LocalDateTime cutoffDate);

}
