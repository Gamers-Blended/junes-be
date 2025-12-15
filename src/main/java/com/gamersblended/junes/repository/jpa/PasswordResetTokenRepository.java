package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Modifying
    @Query(value = "DELETE FROM junes_rel.password_reset_tokens WHERE user_id = :userID", nativeQuery = true)
    void deleteByUserID(@Param("userID") UUID userID);

}
