package com.pdftools.controller;

import com.pdftools.service.PdfSignatureService;
import com.pdftools.service.S3Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PdfController {

    private static final Logger LOG = LoggerFactory.getLogger(PdfController.class);

    private final S3Service s3Service;
    private final PdfSignatureService pdfSignatureService;

    public PdfController(S3Service s3Service, PdfSignatureService pdfSignatureService) {
        this.s3Service = s3Service;
        this.pdfSignatureService = pdfSignatureService;
    }

    /**
     * Receives a request with {@code bucket} and {@code path}, downloads the signed PDF
     * from S3, removes digital signatures, uploads the clean PDF to the same bucket
     * under a "sem-certificado" subdirectory, and returns the output S3 path.
     *
     * <p>Request body example:
     * <pre>{ "bucket": "my-bucket", "path": "docs/assinado.pdf" }</pre>
     *
     * <p>Response example:
     * <pre>{ "output": "docs/sem-certificado/assinado.pdf" }</pre>
     */
    @PostMapping("/remove-signature")
    public ResponseEntity<Map<String, String>> removeSignature(
            @RequestBody RemoveSignatureRequest request) {

        String bucket = request.bucket();
        String inputKey = request.path();

        LOG.info("Request received — bucket: {}, path: {}", bucket, inputKey);

        if (bucket == null || bucket.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Parameter 'bucket' is required"));
        }
        if (inputKey == null || inputKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Parameter 'path' is required"));
        }

        Path tempInput = null;
        Path tempOutput = null;

        try {
            // 1. Download from S3
            tempInput = s3Service.downloadFile(bucket, inputKey);

            // 2. Remove signatures
            tempOutput = Files.createTempFile("pdf-output-", ".pdf");
            pdfSignatureService.removeSignatures(tempInput, tempOutput);

            // 3. Upload to S3 under "sem-certificado" subdirectory
            String outputKey = s3Service.buildOutputKey(inputKey);
            s3Service.uploadFile(bucket, outputKey, tempOutput);

            LOG.info("Done — output: s3://{}/{}", bucket, outputKey);

            return ResponseEntity.ok(Map.of("output", outputKey));

        } catch (IOException e) {
            LOG.error("Failed to process PDF: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process PDF: " + e.getMessage()));
        } finally {
            deleteTempFile(tempInput);
            deleteTempFile(tempOutput);
        }
    }

    private void deleteTempFile(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.warn("Could not delete temp file {}: {}", file, e.getMessage());
            }
        }
    }

    public record RemoveSignatureRequest(String bucket, String path) {
    }
}
