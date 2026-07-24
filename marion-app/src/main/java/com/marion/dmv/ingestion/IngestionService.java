package com.marion.dmv.ingestion;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
public class IngestionService {

    private final EmbeddingStoreIngestor ingestor;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbc;
    private final Timer ingestTimer;

    @Value("${corpus.base-path:C:/DEVL/TITLE/test-data/corpus}")
    private String corpusBasePath;

    public IngestionService(EmbeddingStoreIngestor ingestor,
                            EmbeddingStore<TextSegment> embeddingStore,
                            JdbcTemplate jdbc,
                            MeterRegistry meterRegistry) {
        this.ingestor = ingestor;
        this.embeddingStore = embeddingStore;
        this.jdbc = jdbc;
        this.ingestTimer = Timer.builder("marion.ingest")
                .description("Document ingestion time")
                .tag("node", "ingestor")
                .register(meterRegistry);
    }

    // Origin-state profile file names — tagged separately for distractor analysis
    private static final List<String> ORIGIN_PROFILE_FILES = List.of(
            "origin-state-verdana.md",
            "origin-state-crestwood.md",
            "origin-state-halloway.md",
            "origin-state-pembrook.md"
    );

    public int resetAndIngest() {
        embeddingStore.removeAll();

        Path base = Paths.get(corpusBasePath);
        if (!Files.isDirectory(base)) {
            throw new IllegalStateException("Corpus directory not found: " + base.toAbsolutePath());
        }

        var parser = new ApacheTikaDocumentParser();
        int count = 0;

        try (Stream<Path> files = Files.list(base)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".md"))::iterator) {
                String fileName = file.getFileName().toString();
                String sourceType = ORIGIN_PROFILE_FILES.contains(fileName) ? "origin_profile" : "regulation";

                ingestTimer.record(() -> {
                    Document doc = FileSystemDocumentLoader.loadDocument(file, parser);
                    doc.metadata().put("source", fileName);
                    doc.metadata().put("source_type", sourceType);
                    ingestor.ingest(doc);
                });
                count++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read corpus directory: " + base, e);
        }

        System.out.println(">>> Ingested " + count + " documents from " + base);
        return count;
    }
}
