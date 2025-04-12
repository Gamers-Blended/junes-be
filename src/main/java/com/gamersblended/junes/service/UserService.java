package com.gamersblended.junes.service;

import com.gamersblended.junes.model.Users;
import com.gamersblended.junes.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UsersRepository usersRepository;

    public List<Users> getAllUsers() {
        List<Users> res = usersRepository.getAllUsers();
        log.info("Total number of users returned from db: {}", res.size());
        return res;

    }
}
