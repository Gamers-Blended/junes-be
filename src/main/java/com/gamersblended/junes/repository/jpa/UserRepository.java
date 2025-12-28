package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("jpaUsersRepository") // JPA repository bean
public interface UserRepository extends JpaRepository<User, Integer> {

    @Query(value = "SELECT UNNEST(history_list) FROM junes_rel.users WHERE id = :id", nativeQuery = true)
    List<String> getUserHistory(@Param("id") Integer id);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM junes_rel.users WHERE LOWER(email) = LOWER(:email) AND is_email_verified = true) AS email_exists", nativeQuery = true)
    Boolean isEmailVerified(@Param("email") String email);

    @Modifying
    @Query(value = "DELETE FROM junes_rel.users WHERE email = :email AND is_email_verified = false", nativeQuery = true)
    int deleteAllUnverifiedRecordsForEmail(@Param("email") String email);

    @Query(value = "SELECT * FROM junes_rel.users WHERE LOWER(email) = LOWER(:email)", nativeQuery = true)
    Optional<User> getUserByEmail(@Param("email") String email);

    @Query(value = "SELECT email FROM junes_rel.users WHERE user_id = :userID", nativeQuery = true)
    Optional<String> getUserEmail(@Param("userID") UUID userID);
}
