package com.cugcoding.forum.rag;

import com.cugcoding.forum.model.Post;
import com.cugcoding.forum.repo.ForumRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int CHUNK_SIZE = 500;   // characters per chunk
    private static final int TOP_K = 5;           // top results to retrieve

    private final ForumRepository repository;
    private final DashScopeClient dashScopeClient;

    public RagService(ForumRepository repository, DashScopeClient dashScopeClient) {
        this.repository = repository;
        this.dashScopeClient = dashScopeClient;
    }

    // ======================== Indexing ========================

    /** Rebuild all knowledge chunks from articles and uploaded files. */
    public int rebuildAllChunks() {
        repository.clearKnowledgeChunks();
        int count = 0;
        for (Post post : repository.findPosts()) {
            count += indexArticle(post);
        }
        log.info("Rebuilt knowledge index: {} chunks", count);
        return count;
    }

    /** Index a single article into chunks. */
    public int indexArticle(Post post) {
        String text = "# " + post.getTitle() + "\n\n" + post.getContent();
        return saveChunks("ARTICLE", post.getId(), post.getTitle(), text);
    }

    /** Parse and index an uploaded file. */
    public int indexFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String text = parseFile(file);
        if (text.isEmpty()) throw new IllegalArgumentException("无法解析文件内容");
        return saveChunks("FILE", 0L, filename != null ? filename : "uploaded_file", text);
    }

    /** Parse text from uploaded file (PDF, DOCX, TXT, MD). */
    public String parseFile(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null) name = "";
        String lower = name.toLowerCase();

        if (lower.endsWith(".pdf")) {
            try (PDDocument doc = PDDocument.load(file.getInputStream())) {
                return new PDFTextStripper().getText(doc);
            }
        } else if (lower.endsWith(".docx")) {
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                return new XWPFWordExtractor(doc).getText();
            }
        } else if (lower.endsWith(".doc")) {
            throw new IllegalArgumentException("暂不支持旧版 .doc 格式，请转换为 .docx");
        } else {
            // TXT, MD, or any text file
            return new String(file.getBytes(), "UTF-8");
        }
    }

    private int saveChunks(String type, Long sourceId, String sourceName, String text) {
        List<String> chunks = splitText(text);
        int idx = 0;
        for (String chunk : chunks) {
            repository.insertKnowledgeChunk(type, sourceId, sourceName, idx++, chunk.trim());
        }
        return chunks.size();
    }

    /** Split text into overlapping chunks. */
    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return chunks;
        // Split by paragraphs first, then merge small ones
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder buf = new StringBuilder();
        for (String p : paragraphs) {
            if (buf.length() + p.length() > CHUNK_SIZE && buf.length() > 0) {
                chunks.add(buf.toString());
                buf.setLength(0);
            }
            if (buf.length() > 0) buf.append("\n\n");
            buf.append(p);
        }
        if (buf.length() > 0) chunks.add(buf.toString());
        return chunks;
    }

    // ======================== Retrieval ========================

    /** Retrieve top_k relevant chunks using DashScope rerank. */
    public List<Map<String, Object>> retrieve(String query, List<Long> articleIds, int topK) {
        // Get candidate chunks
        List<KnowledgeChunk> candidates;
        if (articleIds != null && !articleIds.isEmpty()) {
            candidates = repository.findChunksByArticleIds(articleIds);
        } else {
            candidates = repository.findAllKnowledgeChunks();
        }

        if (candidates.isEmpty()) return Collections.emptyList();

        List<String> documents = candidates.stream().map(KnowledgeChunk::getContent).collect(Collectors.toList());
        List<Integer> ranked = dashScopeClient.rerank(query, documents, Math.min(topK, candidates.size()));

        List<Map<String, Object>> results = new ArrayList<>();
        for (int idx : ranked) {
            if (idx < candidates.size()) {
                KnowledgeChunk c = candidates.get(idx);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("content", c.getContent());
                r.put("sourceName", c.getSourceName());
                r.put("sourceType", c.getSourceType());
                results.add(r);
            }
        }
        return results;
    }

    // ======================== Query ========================

    public List<Post> listIndexedPosts() {
        return repository.findPosts();
    }

    public List<Map<String, Object>> listUploadedFiles() {
        List<KnowledgeChunk> chunks = repository.findAllKnowledgeChunks();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> files = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            if ("FILE".equals(c.getSourceType()) && seen.add(c.getSourceName())) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("name", c.getSourceName());
                files.add(f);
            }
        }
        return files;
    }

    // ======================== Chat ========================

    /** Full RAG chat: retrieve → build prompt → stream LLM. */
    public void chat(String query, List<Long> articleIds, Consumer<String> onChunk) {
        // 1. Retrieve relevant chunks
        List<Map<String, Object>> chunks = retrieve(query, articleIds, TOP_K);

        // 2. Build context from chunks
        StringBuilder context = new StringBuilder();
        if (!chunks.isEmpty()) {
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> c = chunks.get(i);
                context.append("[").append(i + 1).append("] (").append(c.get("sourceName")).append(")\n");
                context.append(c.get("content")).append("\n\n");
            }
        }

        // 3. Build prompt
        String systemPrompt = "你是 CUGCoding 校园论坛的 AI 助手。请基于提供的参考知识来回答问题。\n\n" +
                "规则：\n" +
                "1. 优先基于下面的参考信息回答，如果参考信息不足以回答问题，请如实说明\n" +
                "2. 回答时引用来源，使用 [文档N] 格式\n" +
                "3. 回答要简洁、准确、专业\n" +
                "4. 用中文回答\n\n" +
                "参考知识：\n" + (context.length() > 0 ? context.toString() : "（无相关参考知识）");

        // 4. Stream response
        dashScopeClient.chatStream(systemPrompt, query, onChunk);
    }
}
