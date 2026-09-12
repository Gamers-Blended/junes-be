package com.gamersblended.junes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncConfigTest {

    @Mock
    private ThreadPoolTaskExecutorBuilder builder;

    @Mock
    private ThreadPoolTaskExecutor executor;

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    void taskExecutor_returnsExecutorBuiltByProvidedBuilder() {
        when(builder.build()).thenReturn(executor);

        ThreadPoolTaskExecutor result = asyncConfig.taskExecutor(builder);

        assertThat(result).isSameAs(executor);
        verify(builder).build();
    }
}
