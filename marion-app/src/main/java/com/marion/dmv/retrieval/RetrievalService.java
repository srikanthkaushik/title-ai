package com.marion.dmv.retrieval;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.marion.dmv.rerank.RerankingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbc;
    private final RerankingService rerankingService;
    private final Timer retrieveTimer;

    @Value("${rag.retrieve-multiplier:3}")
    private int retrieveMultiplier;

    @Value("${rag.top-k:5}")
    private int topK;

    public RetrievalService(EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            JdbcTemplate jdbc,
                            RerankingService rerankingService,
                            MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.jdbc = jdbc;
        this.rerankingService = rerankingService;
        this.retrieveTimer = Timer.builder("marion.retrieve")
                .description("Hybrid retrieval time")
                .tag("node", "retriever")
                .register(meterRegistry);
    }

    public List<RetrievalResult> retrieveAndRerank(String query) {
        return retrieveTimer.record(() -> {
            int candidateCount = topK * retrieveMultiplier;
            List<RetrievalResult> candidates = new ArrayList<>();

            // 1. Vector search
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> vectorMatches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(candidateCount)
                            .minScore(0.0)
                            .build()
            ).matches();

            for (EmbeddingMatch<TextSegment> match : vectorMatches) {
                candidates.add(new RetrievalResult(
                        match.embedded().text(),
                        match.embedded().metadata().getString("source"),
                        match.embedded().metadata().getString("source_type"),
                        match.score()
                ));
            }

            // 2. FTS search — merge by source text to de-duplicate with vector results
            List<RetrievalResult> ftsResults = fullTextSearch(query, candidateCount);
            Map<String, RetrievalResult> merged = new LinkedHashMap<>();
            for (RetrievalResult r : candidates) {
                merged.put(r.text(), r);
            }
            for (RetrievalResult r : ftsResults) {
                merged.putIfAbsent(r.text(), r);
            }

            List<RetrievalResult> combined = new ArrayList<>(merged.values());

            // 3. Rerank
            return rerankingService.rerank(query, combined, topK);
        });
    }

    private List<RetrievalResult> fullTextSearch(String query, int limit) {
        String tsQuery = toTsQuery(query);
        try {
            return jdbc.query(
                    """
                    SELECT text,
                           metadata->>'source'      AS source,
                           metadata->>'source_type' AS source_type,
                           ts_rank(fts, to_tsquery('english', ?)) AS score
                    FROM doc_embeddings
                    WHERE fts @@ to_tsquery('english', ?)
                    ORDER BY score DESC
                    LIMIT ?
                    """,
                    (rs, row) -> new RetrievalResult(
                            rs.getString("text"),
                            rs.getString("source"),
                            rs.getString("source_type"),
                            rs.getDouble("score")
                    ),
                    tsQuery, tsQuery, limit
            );
        } catch (Exception e) {
            // FTS column may not exist yet on first boot before ingest
            System.err.println(">>> FTS search skipped: " + e.getMessage());
            return List.of();
        }
    }

    // Convert a free-text query into a simple tsquery (AND of lexemes)
    private static String toTsQuery(String query) {
        return query.trim()
                .replaceAll("[^a-zA-Z0-9 ]", " ")
                .trim()
                .replaceAll("\\s+", " & ");
    }
}
