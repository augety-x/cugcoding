package com.cugcoding.forum.oss;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class ObsUploader {

    private static final Logger log = LoggerFactory.getLogger(ObsUploader.class);

    private final ObsConfig config;
    private ObsClient obsClient;

    public ObsUploader(ObsConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        obsClient = new ObsClient(config.getAk(), config.getSk(), config.getEndpoint());
        log.info("OBS client initialized, endpoint={}, bucket={}", config.getEndpoint(), config.getBucket());
    }

    @PreDestroy
    public void destroy() {
        if (obsClient != null) {
            try { obsClient.close(); } catch (IOException e) { log.warn("Error closing OBS client", e); }
        }
    }

    /**
     * Upload image bytes to OBS. Returns the full public URL on success, empty string on failure.
     */
    public String upload(byte[] bytes, String fileType) {
        try {
            // Use MD5 as filename to deduplicate
            String md5 = md5(bytes);
            String key = config.getPrefix() + md5 + "." + (fileType != null ? fileType : "png");

            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            PutObjectRequest request = new PutObjectRequest(config.getBucket(), key, input);
            PutObjectResult result = obsClient.putObject(request);

            if (result.getStatusCode() == 200) {
                String url = config.getHost() + key;
                log.info("OBS upload success: {}", url);
                return url;
            } else {
                log.error("OBS upload failed, status={}", result.getStatusCode());
                return "";
            }
        } catch (ObsException e) {
            log.error("OBS error: code={}, msg={}", e.getResponseCode(), e.getErrorMessage());
            return "";
        } catch (Exception e) {
            log.error("Upload error", e);
            return "";
        }
    }

    /**
     * Upload from InputStream. Determines file type from magic bytes if not specified.
     */
    public String upload(InputStream input, String fileType) {
        try {
            byte[] bytes = readAllBytes(input);
            return upload(bytes, fileType);
        } catch (IOException e) {
            log.error("Failed to read upload stream", e);
            return "";
        }
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    private String md5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
