package com.cugcoding.forum.rag;

import com.cugcoding.forum.auth.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private static final Logger log = LoggerFactory.getLogger(RagController.class);
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** Upload a knowledge file (PDF/DOCX/TXT/MD) and index its content. */
    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) {
        AuthContext.requireLogin();
        if (file.isEmpty()) throw new IllegalArgumentException("请选择文件");
        try {
            int chunks = ragService.indexFile(file);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filename", file.getOriginalFilename());
            result.put("chunks", chunks);
            result.put("message", "文件已解析并索引，共 " + chunks + " 个文本块");
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            log.error("File upload error", e);
            throw new RuntimeException("文件处理失败: " + e.getMessage());
        }
    }

    /** Admin: rebuild the entire knowledge base. */
    @PostMapping("/admin/rebuild")
    public Map<String, Object> rebuild() {
        AuthContext.requireAdmin();
        int count = ragService.rebuildAllChunks();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunks", count);
        result.put("message", "知识库已重建，共 " + count + " 个文本块");
        return result;
    }

    /** Get all knowledge sources (articles + files) for the selection UI. */
    @GetMapping("/sources")
    public Map<String, Object> getSources() {
        AuthContext.requireLogin();
        // Return available article IDs for selection
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> articles = new ArrayList<>();
        for (com.cugcoding.forum.model.Post post : ragService.listIndexedPosts()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", post.getId());
            a.put("title", post.getTitle());
            articles.add(a);
        }
        // Also list uploaded files
        result.put("articles", articles);
        result.put("files", ragService.listUploadedFiles());
        return result;
    }
}
