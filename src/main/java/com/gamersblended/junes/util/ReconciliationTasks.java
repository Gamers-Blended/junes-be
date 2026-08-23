package com.gamersblended.junes.util;

import com.gamersblended.junes.service.ReconciliationService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReconciliationTasks {

    private final ReconciliationService reconciliationService;

    public ReconciliationTasks(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(cron = "${reconciliation.visibility-check.cron:0 0 * * * *}")
    @SchedulerLock(name = "ReconciliationVisibilityCheckTask", lockAtMostFor = "${reconciliation.visibility-check.lock-at-most}", lockAtLeastFor = "${reconciliation.visibility-check.lock-at-least}")
    public void scheduledLogUnresolvedFailures() {
        log.info("Starting scheduled reconciliation visibility check...");
        reconciliationService.logUnresolvedFailures();
    }
}