package com.gamersblended.junes.repository;

import com.gamersblended.junes.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsersRepository extends JpaRepository<Users, Integer> {

    @Query(value = "SELECT * FROM customer_data.users", nativeQuery = true)
    List<Users> getAllUsers();
}
