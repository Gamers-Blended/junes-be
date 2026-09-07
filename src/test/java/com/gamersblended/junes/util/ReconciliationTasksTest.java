package com.gamersblended.junes.util;

import com.gamersblended.junes.service.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationTasksTest {

    @Mock
    private ReconciliationService reconciliationService;

    @InjectMocks
    private ReconciliationTasks reconciliationTasks;

    @Test
    void scheduledLogUnresolvedFailures_delegatesToReconciliationServiceOnly() {
        reconciliationTasks.scheduledLogUnresolvedFailures();

        verify(reconciliationService).logUnresolvedFailures();
        verifyNoMoreInteractions(reconciliationService);
    }

    @Test
    void scheduledLogUnresolvedFailures_propagatesExceptionFromReconciliationService() {
        doThrow(new RuntimeException("boom")).when(reconciliationService).logUnresolvedFailures();

        assertThatThrownBy(() -> reconciliationTasks.scheduledLogUnresolvedFailures())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
