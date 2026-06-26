package com.netzero.pipeline.scheduler;

import com.netzero.pipeline.service.DailyPipelineService;
import com.netzero.store.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Triggers the daily pipeline for all registered stores.
 * Runs at 02:00 KST every day.
 */
@Component
public class PipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyPipelineService pipelineService;
    private final StoreRepository storeRepository;

    public PipelineScheduler(DailyPipelineService pipelineService, StoreRepository storeRepository) {
        this.pipelineService = pipelineService;
        this.storeRepository = storeRepository;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        LocalDate today = LocalDate.now(KST);
        log.info("PipelineScheduler triggered for {}", today);
        storeRepository.findAll().forEach(store -> {
            try {
                var result = pipelineService.run(store.getId(), today);
                log.info("Pipeline complete storeId={} dueItems={} recommended={} elapsedMs={}",
                        store.getId(), result.dueItems(), result.recommended(), result.elapsedMs());
            } catch (Exception e) {
                log.error("Pipeline failed storeId={} date={}", store.getId(), today, e);
            }
        });
    }
}
