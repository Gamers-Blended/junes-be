package com.gamersblended.junes;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal {@code @SpringBootConfiguration} used as the context source for {@code @WebMvcTest}
 * controller slice tests, in place of the real {@link JunesApplication}.
 * <p>
 * {@link JunesApplication} declares {@code @EnableMongoRepositories}/{@code @EnableJpaRepositories}
 * directly on itself rather than behind Spring Boot auto-configuration, so any {@code @WebMvcTest}
 * that uses it as its configuration source tries to build every Mongo/JPA repository bean regardless
 * of slice scope, requiring a real {@code MongoTemplate}/{@code EntityManagerFactory}. This class
 * shares {@link JunesApplication}'s package (so component scanning still reaches the same
 * {@code Filter}/{@code @ControllerAdvice}/{@code WebMvcConfigurer} beans a real slice test needs)
 * without those repository-enabling annotations.
 */
@SpringBootApplication
public class WebMvcTestApplication {
}
