package com.marion.dmv.ingestion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    // Wipes doc_embeddings and re-ingests the full corpus.
    // Requires confirm=true to prevent accidental erasure.
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(
            @RequestParam(defaultValue = "false") boolean confirm) {

        if (!confirm) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Pass ?confirm=true to confirm corpus wipe and reindex"));
        }

        int count = ingestionService.resetAndIngest();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "documentsIngested", count
        ));
    }
}
