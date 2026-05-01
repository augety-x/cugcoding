package com.cugcoding.forum.search;

import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostIndexService {

    private static final Logger log = LoggerFactory.getLogger(PostIndexService.class);

    private final ElasticsearchRestTemplate esTemplate;

    public PostIndexService(ElasticsearchRestTemplate esTemplate) {
        this.esTemplate = esTemplate;
    }

    /** Index or update a single post document. */
    public void index(PostDocument doc) {
        IndexQuery query = new IndexQueryBuilder().withId(String.valueOf(doc.getId())).withObject(doc).build();
        esTemplate.index(query, esTemplate.getIndexCoordinatesFor(PostDocument.class));
    }

    /** Bulk index a list of posts. */
    public void bulkIndex(List<PostDocument> docs) {
        if (docs.isEmpty()) return;
        List<IndexQuery> queries = docs.stream()
                .map(doc -> new IndexQueryBuilder().withId(String.valueOf(doc.getId())).withObject(doc).build())
                .collect(Collectors.toList());
        esTemplate.bulkIndex(queries, PostDocument.class);
        log.info("Bulk indexed {} posts", docs.size());
    }

    /** Delete a post from the index. */
    public void delete(Long postId) {
        esTemplate.delete(String.valueOf(postId), PostDocument.class);
    }

    /** Search posts by keyword using multi_match on title and content. */
    public SearchHits<PostDocument> search(String keyword, int page, int size) {
        MultiMatchQueryBuilder mmq = QueryBuilders.multiMatchQuery(keyword, "title", "content")
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS);
        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(mmq)
                .withPageable(PageRequest.of(page, size))
                .build();
        return esTemplate.search(query, PostDocument.class);
    }

    /** Count total matches for a keyword. */
    public long count(String keyword) {
        MultiMatchQueryBuilder mmq = QueryBuilders.multiMatchQuery(keyword, "title", "content");
        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(mmq)
                .withMaxResults(0)
                .build();
        return esTemplate.count(query, PostDocument.class);
    }
}
