package com.marion.dmv.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Serves a single corpus document's raw markdown, so the UI's "sources" citations can link back
// to the actual regulation/procedure text the model cited. filename is validated against the
// corpus directory itself (no path traversal, must actually resolve inside it and exist) rather
// than trusted as given — it ultimately comes from LLM-generated text, not a user form field.
@RestController
@RequestMapping("/api/corpus")
public class CorpusController {

    @Value("${corpus.base-path:C:/DEVL/TITLE/test-data/corpus}")
    private String corpusBasePath;

    // {filename:.+} — without the regex, Spring's default path matching treats a trailing dot
    // in the last path segment as a format-suffix separator and strips it, so "foo.md" would
    // bind filename="foo" and the route wouldn't match at all (404, not even reaching this code).
    @GetMapping(value = "/{filename:.+}", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> getDocument(@PathVariable String filename) {
        if (!filename.endsWith(".md") || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }

        Path base = Paths.get(corpusBasePath).normalize().toAbsolutePath();
        Path target = base.resolve(filename).normalize();
        if (!target.startsWith(base) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source document not found: " + filename);
        }

        try {
            return ResponseEntity.ok(Files.readString(target));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read document");
        }
    }
}
