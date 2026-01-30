package com.smartdocflow.ai.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Download file from MinIO to temp directory
     * @param objectName Object name in MinIO (e.g., "jobId/filename.pdf")
     * @return Local File object
     */
    public File downloadFileToTemp(String objectName) {
        try {
            log.info("Downloading from MinIO: {}", objectName);

            // Create temp directory
            Path tempDir = Files.createTempDirectory("smartdocflow-ai");

            // Extract filename from object name
            String filename = objectName.substring(objectName.lastIndexOf('/') + 1);
            File tempFile = new File(tempDir.toFile(), filename);

            // Download from MinIO
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                stream.transferTo(fos);
            }

            log.info("Downloaded to temp file: {}", tempFile.getAbsolutePath());
            return tempFile;

        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", objectName, e);
            throw new RuntimeException("Failed to download file from MinIO", e);
        }
    }

    /**
     * Delete temporary file after processing
     * @param file File to delete
     */
    public void deleteTempFile(File file) {
        try {
            if (file != null && file.exists()) {
                file.delete();
                // Also delete parent temp directory if empty
                File parent = file.getParentFile();
                if (parent != null && parent.list().length == 0) {
                    parent.delete();
                }
                log.info("Deleted temp file: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Failed to delete temp file: {}", file.getAbsolutePath(), e);
        }
    }
}