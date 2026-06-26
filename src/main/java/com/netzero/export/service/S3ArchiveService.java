package com.netzero.export.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

@Service
public class S3ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(S3ArchiveService.class);

    @Value("${storage.s3.bucket:zerowave}")
    private String bucket;

    @Autowired(required = false)
    private S3Client s3;

    public void upload(String key, String csvBody) {
        if (s3 == null) {
            log.info("S3 disabled — skipping upload of key={}", key);
            return;
        }
        byte[] bytes = csvBody.getBytes(StandardCharsets.UTF_8);
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("text/csv")
                .contentLength((long) bytes.length)
                .build(),
            RequestBody.fromBytes(bytes)
        );
        log.info("Uploaded s3://{}/{}", bucket, key);
    }
}
