package com.gamersblended.junes.config;

import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    private static final String HOST = "redis-host";
    private static final int PORT = 6380;

    private final RedisConfig redisConfig = new RedisConfig();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisConfig, "redisHost", HOST);
        ReflectionTestUtils.setField(redisConfig, "redisPort", PORT);
        ReflectionTestUtils.setField(redisConfig, "redisPassword", "");
    }

    @Test
    void redisConnectionFactory_configuresHostAndPort_withoutPassword() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();

        assertThat(factory).isInstanceOf(LettuceConnectionFactory.class);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        assertThat(lettuceFactory.getHostName()).isEqualTo(HOST);
        assertThat(lettuceFactory.getPort()).isEqualTo(PORT);
        assertThat(lettuceFactory.getPassword()).isNull();
    }

    @Test
    void redisConnectionFactory_setsPassword_whenConfigured() {
        ReflectionTestUtils.setField(redisConfig, "redisPassword", "s3cret");

        LettuceConnectionFactory factory = (LettuceConnectionFactory) redisConfig.redisConnectionFactory();

        assertThat(factory.getPassword()).isEqualTo("s3cret");
    }

    @Test
    void redisTemplate_usesStringSerializersForAllFields() {
        RedisConnectionFactory connectionFactory = redisConfig.redisConnectionFactory();

        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(StringRedisSerializer.class);
    }

    @Test
    void recommendationRedisTemplate_usesStringKeyAndJacksonValueSerializer() {
        RedisConnectionFactory connectionFactory = redisConfig.redisConnectionFactory();

        RedisTemplate<String, RecommendationResponseDTO> template =
                redisConfig.recommendationRedisTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
    }
}
