package com.smartdocflow.api.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Upload file to MinIO
     * @param file MultipartFile to upload
     * @param jobId Job ID for organizing files
     * @return Object name (path) in MinIO
     */
    public String uploadFile(MultipartFile file, String jobId) {
        try {
            String originalFilename = file.getOriginalFilename();
            String objectName = jobId + "/" + originalFilename;

            // Upload file to MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("File uploaded to MinIO: {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    /**
     * Download file from MinIO
     * @param objectName Object name in MinIO
     * @return InputStream of the file
     */
    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", objectName, e);
            throw new RuntimeException("Failed to download file from MinIO", e);
        }
    }

    /**
     * Delete file from MinIO
     * @param objectName Object name in MinIO
     */
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("File deleted from MinIO: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", objectName, e);
            throw new RuntimeException("Failed to delete file from MinIO", e);
        }
    }

    /**
     * Get MinIO URL for a file
     * @param objectName Object name in MinIO
     * @return Full MinIO URL
     */
    public String getFileUrl(String objectName) {
        // Construct URL manually from configured minioUrl
        return String.format("%s/%s/%s", minioUrl, bucketName, objectName);
    }
}