package com.pdftools.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3Service {

    private static final Logger LOG = LoggerFactory.getLogger(S3Service.class);

    private final S3Client s3Client;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Downloads a file from S3 to a local temporary path.
     */
    public Path downloadFile(String bucket, String key) throws IOException {
        LOG.info("Downloading s3://{}/{}", bucket, key);

        String fileName = key.contains("/")
                ? key.substring(key.lastIndexOf('/') + 1)
                : key;
        Path tempFile = Files.createTempFile("pdf-input-", "-" + fileName);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (InputStream s3Stream = s3Client.getObject(request);
             OutputStream fileOut = Files.newOutputStream(tempFile)) {
            s3Stream.transferTo(fileOut);
        }

        LOG.info("Downloaded to {}", tempFile);
        return tempFile;
    }

    /**
     * Uploads a local file to S3.
     */
    public void uploadFile(String bucket, String key, Path localFile) {
        LOG.info("Uploading to s3://{}/{}", bucket, key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/pdf")
                .build();

        s3Client.putObject(request, RequestBody.fromFile(localFile));
        LOG.info("Upload complete: s3://{}/{}", bucket, key);
    }

    /**
     * Builds the output S3 key by inserting "sem-certificado" as a subdirectory.
     *
     * <p>Example: "docs/assinado.pdf" → "docs/sem-certificado/assinado.pdf"
     */
    public String buildOutputKey(String inputKey) {
        int lastSlash = inputKey.lastIndexOf('/');
        if (lastSlash >= 0) {
            String dir = inputKey.substring(0, lastSlash);
            String file = inputKey.substring(lastSlash + 1);
            return dir + "/sem-certificado/" + file;
        }
        return "sem-certificado/" + inputKey;
    }
}
