package com.gamersblended.junes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.gamersblended.junes.repository.mongodb")
@EnableJpaRepositories(basePackages = "com.gamersblended.junes.repository.jpa")
public class JunesApplication {
	public static void main(String[] args) {
		SpringApplication.run(JunesApplication.class, args);
	}

}
