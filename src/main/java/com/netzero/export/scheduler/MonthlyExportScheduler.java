package com.netzero.export.scheduler;

import com.netzero.export.service.InventoryFlowExporter;
import com.netzero.export.service.PresignService;
import com.netzero.export.service.S3ArchiveService;
import com.netzero.export.service.SalesCsvExporter;
import com.netzero.store.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class MonthlyExportScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyExportScheduler.class);

    private final StoreRepository storeRepository;
    private final SalesCsvExporter salesCsvExporter;
    private final InventoryFlowExporter inventoryFlowExporter;
    private final S3ArchiveService s3ArchiveService;

    public MonthlyExportScheduler(
            StoreRepository storeRepository,
            SalesCsvExporter salesCsvExporter,
            InventoryFlowExporter inventoryFlowExporter,
            S3ArchiveService s3ArchiveService) {
        this.storeRepository = storeRepository;
        this.salesCsvExporter = salesCsvExporter;
        this.inventoryFlowExporter = inventoryFlowExporter;
        this.s3ArchiveService = s3ArchiveService;
    }

    /**
     * Runs at 01:00 on days 28–31.
     * The guard inside ensures execution only on the actual last day of the month.
     */
    @Scheduled(cron = "0 0 1 28-31 * *")
    public void exportMonthly() {
        LocalDate today = LocalDate.now();
        if (today.getDayOfMonth() != today.lengthOfMonth()) {
            return; // not the last day — skip
        }
        log.info("MonthlyExportScheduler triggered for {}", today);
        YearMonth ym = YearMonth.from(today);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        storeRepository.findAll().forEach(store -> {
            try {
                exportStore(store.getId(), from, to);
            } catch (Exception e) {
                log.error("Failed to export store={}", store.getId(), e);
            }
        });
    }

    /**
     * Export and upload sales + inventory CSVs for one store over the given period.
     * Also callable directly from the manual archive endpoint.
     */
    public void exportStore(Long storeId, LocalDate from, LocalDate to) throws IOException {
        // Sales CSV
        ByteArrayOutputStream salesOut = new ByteArrayOutputStream();
        salesCsvExporter.export(storeId, from, to, salesOut);
        s3ArchiveService.upload(PresignService.salesKey(storeId, from), salesOut.toString(StandardCharsets.UTF_8));

        // Inventory CSV (snapshot on last day of period)
        ByteArrayOutputStream invOut = new ByteArrayOutputStream();
        inventoryFlowExporter.export(storeId, to, invOut);
        s3ArchiveService.upload(PresignService.inventoryKey(storeId, from), invOut.toString(StandardCharsets.UTF_8));
    }
}
