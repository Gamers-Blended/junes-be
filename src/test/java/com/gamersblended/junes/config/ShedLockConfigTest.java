package com.gamersblended.junes.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ShedLockConfigTest {

    @Mock
    private DataSource dataSource;

    private final ShedLockConfig shedLockConfig = new ShedLockConfig();

    @Test
    void lockProvider_returnsJdbcTemplateBackedLockProvider() {
        LockProvider lockProvider = shedLockConfig.lockProvider(dataSource);

        assertThat(lockProvider).isNotNull().isInstanceOf(JdbcTemplateLockProvider.class);
    }
}
