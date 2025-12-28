package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, UUID> {

    @Query(value = "SELECT EXISTS(SELECT 1 FROM junes_rel.token_blacklist WHERE token = :token) AS token_exists", nativeQuery = true)
    Boolean isTokenExist(@Param("token") String token);
}
