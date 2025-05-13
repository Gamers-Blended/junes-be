package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("jpaUsersRepository") // JPA repository bean
public interface UserRepository extends JpaRepository<User, Integer> {

    @Query(value = "SELECT * FROM customer_data.users", nativeQuery = true)
    List<User> getAllUsers();

    @Query(value = "SELECT UNNEST(history_list) FROM customer_data.users WHERE id = :id", nativeQuery = true)
    List<String> getUserHistory(@Param("id") Integer id);
}
