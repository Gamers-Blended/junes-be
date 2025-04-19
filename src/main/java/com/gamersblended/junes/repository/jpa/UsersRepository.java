package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("jpaUsersRepository") // JPA repository bean
public interface UsersRepository extends JpaRepository<Users, Integer> {

    @Query(value = "SELECT * FROM customer_data.users", nativeQuery = true)
    List<Users> getAllUsers();
}
