package com.marion.dmv.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class RagConfig {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embed-model:nomic-embed-text}")
    private String embedModel;

    @Value("${pgvector.host:localhost}")
    private String pgHost;

    @Value("${pgvector.port:5432}")
    private int pgPort;

    @Value("${pgvector.database:mariondmv}")
    private String pgDatabase;

    @Value("${pgvector.username:marion}")
    private String pgUser;

    @Value("${pgvector.password:marion}")
    private String pgPassword;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embedModel)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        return PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(pgUser)
                .password(pgPassword)
                .table("doc_embeddings")
                .dimension(embeddingModel.dimension())
                .createTable(true)
                .build();
    }

    @Bean
    public EmbeddingStoreIngestor ingestor(EmbeddingModel embeddingModel,
                                            EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    // Adds the FTS column to doc_embeddings after Spring creates the table.
    // Safe to run repeatedly — uses IF NOT EXISTS and ADD COLUMN IF NOT EXISTS.
    @Bean
    public FtsSchemaSetup ftsSchemaSetup(JdbcTemplate jdbcTemplate) {
        return new FtsSchemaSetup(jdbcTemplate);
    }

    public static class FtsSchemaSetup {
        private final JdbcTemplate jdbc;

        FtsSchemaSetup(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void addFtsColumn() {
            try {
                jdbc.execute("""
                        ALTER TABLE doc_embeddings
                        ADD COLUMN IF NOT EXISTS fts tsvector
                        GENERATED ALWAYS AS (to_tsvector('english', COALESCE(text, ''))) STORED
                        """);
                jdbc.execute("""
                        CREATE INDEX IF NOT EXISTS idx_doc_embeddings_fts
                        ON doc_embeddings USING GIN (fts)
                        """);
                System.out.println(">>> FTS column and GIN index ready on doc_embeddings");
            } catch (Exception e) {
                System.err.println(">>> FTS setup warning (table may not exist yet): " + e.getMessage());
            }
        }
    }
}
