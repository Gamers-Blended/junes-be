package com.gamersblended.junes.config;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigTest {

    private static final String HOST = "redis-host";
    private static final int PORT = 6380;

    @Mock
    private RedisClient mockRedisClient;

    @Mock
    private StatefulRedisConnection<String, byte[]> connection;

    @Mock
    private RedisAsyncCommands<String, byte[]> asyncCommands;

    private final RateLimitConfig rateLimitConfig = new RateLimitConfig();
    private RedisClient createdClient;

    @AfterEach
    void tearDown() {
        if (createdClient != null) {
            createdClient.shutdown();
        }
    }

    @Test
    void redisClient_isBuiltForConfiguredHostAndPort() {
        ReflectionTestUtils.setField(rateLimitConfig, "redisHost", HOST);
        ReflectionTestUtils.setField(rateLimitConfig, "redisPort", PORT);

        createdClient = rateLimitConfig.redisClient();

        assertThat(createdClient).isNotNull();
        RedisURI redisURI = (RedisURI) ReflectionTestUtils.getField(createdClient, "redisURI");
        assertThat(redisURI).isNotNull();
        assertThat(redisURI.getHost()).isEqualTo(HOST);
        assertThat(redisURI.getPort()).isEqualTo(PORT);
    }

    @Test
    void proxyManager_isBuiltFromConnectionEstablishedOnProvidedClient() {
        when(mockRedisClient.connect(ArgumentMatchers.<RedisCodec<String, byte[]>>any())).thenReturn(connection);
        when(connection.async()).thenReturn(asyncCommands);

        LettuceBasedProxyManager<String> proxyManager = rateLimitConfig.proxyManager(mockRedisClient);

        assertThat(proxyManager).isNotNull();
    }
}
