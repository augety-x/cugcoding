package com.cugcoding.forum.web;

import com.cugcoding.forum.auth.AuthContext;
import com.cugcoding.forum.oss.ObsUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);
    private static final Set<String> ALLOWED_TYPES = new HashSet<>(
            Arrays.asList("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"));
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB

    private final ObsUploader obsUploader;

    public ImageController(ObsUploader obsUploader) {
        this.obsUploader = obsUploader;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(HttpServletRequest request) {
        AuthContext.requireLogin();

        MultipartFile file = extractFile(request);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("不支持的图片格式，仅支持 PNG/JPG/GIF/WebP");
        }

        try {
            String fileType = contentType.substring(contentType.indexOf('/') + 1);
            if ("jpeg".equals(fileType)) fileType = "jpg";
            String url = obsUploader.upload(file.getInputStream(), fileType);

            if (url.isEmpty()) {
                throw new RuntimeException("上传失败，请稍后重试");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("imagePath", url);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Image upload error", e);
            throw new RuntimeException("上传失败，请稍后重试");
        }
    }

    /** Import a .md file: extract title, upload referenced images to OBS, return processed markdown. */
    @PostMapping("/import-markdown")
    public Map<String, Object> importMarkdown(HttpServletRequest request) {
        AuthContext.requireLogin();

        MultipartFile file = extractFile(request, "file");
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 Markdown 文件");
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String title = extractTitle(content, file.getOriginalFilename());
            content = processImageUrls(content);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", title);
            result.put("content", content);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Markdown import error", e);
            throw new RuntimeException("导入失败：" + e.getMessage());
        }
    }

    /** Extract title from first # heading, or use filename. */
    private String extractTitle(String content, String filename) {
        Matcher m = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE).matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Fallback: use filename without extension
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            return dot > 0 ? filename.substring(0, dot) : filename;
        }
        return "导入的文章";
    }

    /** Find image URLs in markdown, download and re-upload to OBS. */
    private String processImageUrls(String content) {
        Pattern imgPattern = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s\"]+)(?:\\s+\"[^\"]*\")?\\)");
        Matcher m = imgPattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        int count = 0;

        while (m.find()) {
            String alt = m.group(1);
            String url = m.group(2);

            // Skip URLs already on our OBS
            if (url.startsWith("https://cugcoding.obs.")) {
                m.appendReplacement(sb, m.group(0));
                continue;
            }

            // Try to download and re-upload HTTP URLs
            if (url.startsWith("http://") || url.startsWith("https://")) {
                String newUrl = downloadAndUpload(url);
                if (!newUrl.isEmpty()) {
                    count++;
                    m.appendReplacement(sb, "![" + alt + "](" + newUrl + ")");
                    continue;
                }
            }

            // Local paths: keep as-is (can't access from browser)
            m.appendReplacement(sb, m.group(0));
        }
        m.appendTail(sb);
        if (count > 0) {
            log.info("Re-uploaded {} external images to OBS", count);
        }
        return sb.toString();
    }

    /** Download an image from URL and upload to OBS. */
    private String downloadAndUpload(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "CUGCoding-Forum/1.0");
            conn.connect();

            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                conn.disconnect();
                return "";
            }
            if (conn.getContentLength() > 5 * 1024 * 1024) {
                conn.disconnect();
                return ""; // skip files > 5MB
            }

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) != -1) buffer.write(data, 0, n);
            in.close();
            conn.disconnect();

            String fileType = contentType.substring(contentType.indexOf('/') + 1);
            if ("jpeg".equals(fileType)) fileType = "jpg";
            return obsUploader.upload(buffer.toByteArray(), fileType);
        } catch (Exception e) {
            log.warn("Failed to download/re-upload image {}: {}", imageUrl, e.getMessage());
            return "";
        }
    }

    private MultipartFile extractFile(HttpServletRequest request) {
        return extractFile(request, "image");
    }

    private MultipartFile extractFile(HttpServletRequest request, String key) {
        if (request instanceof MultipartHttpServletRequest) {
            return ((MultipartHttpServletRequest) request).getFile(key);
        }
        return null;
    }
}
