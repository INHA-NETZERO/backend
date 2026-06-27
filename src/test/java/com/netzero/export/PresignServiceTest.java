package com.netzero.export;

import com.netzero.export.service.PresignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies PresignService key-construction logic and the S3-disabled fallback path.
 *
 * In tests, {@code storage.s3.enabled} is not set, so S3Config is inactive and
 * S3Presigner is never created. PresignService gracefully returns an
 * {@code s3://bucket/key} placeholder instead of a real presigned URL.
 */
@SpringBootTest
class PresignServiceTest {

    @Autowired
    PresignService presignService;

    @Test
    void salesKey_hasCorrectFormat() {
        String key = PresignService.salesKey(1L, LocalDate.parse("2026-05-01"));
        assertThat(key).isEqualTo("sales/store1/sales-2026-05.csv");
    }

    @Test
    void inventoryKey_hasCorrectFormat() {
        String key = PresignService.inventoryKey(1L, LocalDate.parse("2026-05-01"));
        assertThat(key).isEqualTo("inventory/store1/inventory-2026-05.csv");
    }

    @Test
    void presignGet_withoutS3_returnsPlaceholderContainingKey() {
        String url = presignService.presignGet("sales/store1/sales-2026-05.csv");
        assertThat(url).contains("sales/store1/sales-2026-05.csv");
    }

    @Test
    void recentSalesUrls_returnsOneUrlInDemoMode() {
        // Demo mode: sales-demo-key defaults to data/sales_demo.csv → always 1 URL
        List<String> urls = presignService.recentSalesUrls(1L, LocalDate.parse("2026-06-01"), 2);
        assertThat(urls).hasSize(1);
    }

    @Test
    void recentSalesUrls_demoUrlContainsDemoKey() {
        List<String> urls = presignService.recentSalesUrls(1L, LocalDate.parse("2026-06-01"), 2);
        assertThat(urls.get(0)).contains("sales_demo.csv");
    }
}
