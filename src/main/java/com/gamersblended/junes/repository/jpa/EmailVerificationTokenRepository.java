package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.constant.TokenPurpose;
import com.gamersblended.junes.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Query(value = "SELECT * FROM junes_rel.email_verification_tokens WHERE token_hash = :token", nativeQuery = true)
    Optional<EmailVerificationToken> getTokenEntityByToken(@Param("token") String token);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE junes_rel.email_verification_tokens SET used = true WHERE user_id = :userID AND purpose = CAST(:purpose AS VARCHAR) AND used = false", nativeQuery = true)
    void invalidateActiveTokens(UUID userID, TokenPurpose purpose);
}
