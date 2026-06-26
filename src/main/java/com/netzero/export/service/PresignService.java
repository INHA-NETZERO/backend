package com.netzero.export.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PresignService {

    private static final Logger log = LoggerFactory.getLogger(PresignService.class);

    @Value("${storage.s3.bucket:zerowave}")
    private String bucket;

    @Value("${storage.s3.presign-expiry-seconds:600}")
    private long presignExpirySeconds;

    @Autowired(required = false)
    private S3Presigner presigner;

    /**
     * Returns a presigned GET URL for the given S3 key.
     * Falls back to an {@code s3://bucket/key} placeholder when S3 is disabled.
     */
    public String presignGet(String key) {
        if (presigner == null) {
            log.debug("S3 presigner not available — returning placeholder for key={}", key);
            return "s3://" + bucket + "/" + key;
        }
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(presignExpirySeconds))
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())
            .build();
        return presigner.presignGetObject(request).url().toString();
    }

    /**
     * Returns presigned URLs for the most recent {@code months} sales CSVs for the given store.
     * Month order: oldest first (e.g., for ref=2026-06-01 and months=2: [2026-05, 2026-06]).
     */
    public List<String> recentSalesUrls(Long storeId, LocalDate ref, int months) {
        List<String> urls = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            LocalDate month = ref.minusMonths(i).withDayOfMonth(1);
            urls.add(presignGet(salesKey(storeId, month)));
        }
        return urls;
    }

    /** S3 key for a monthly sales CSV. */
    public static String salesKey(Long storeId, LocalDate month) {
        return String.format("sales/store%d/sales-%s.csv", storeId, yearMonth(month));
    }

    /** S3 key for a monthly inventory CSV. */
    public static String inventoryKey(Long storeId, LocalDate month) {
        return String.format("inventory/store%d/inventory-%s.csv", storeId, yearMonth(month));
    }

    private static String yearMonth(LocalDate d) {
        return d.toString().substring(0, 7); // YYYY-MM
    }
}
