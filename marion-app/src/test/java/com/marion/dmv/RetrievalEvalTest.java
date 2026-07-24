package com.marion.dmv;

import com.marion.dmv.retrieval.RetrievalResult;
import com.marion.dmv.retrieval.RetrievalService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic retrieval eval — asserts that the correct source document appears
 * in top-5 results for each eval question. Requires Ollama + pgvector to be running
 * and the corpus to have been ingested via POST /api/ingest/reset?confirm=true.
 *
 * Remove @Disabled and run against a live environment.
 */
@Disabled("Integration test — requires Ollama + pgvector + ingested corpus")
@SpringBootTest
class RetrievalEvalTest {

    @Autowired
    private RetrievalService retrievalService;

    // A1 — Purchase paper, no lien, happy path
    @Test
    void a1_purchasePaperNoLien_shouldRetrieveProcedureChapter4_1() {
        List<RetrievalResult> results = retrievalService.retrieveAndRerank(
                "What documents must a customer present to transfer a paper title from Crestwood "
                + "when purchasing a vehicle with no active lien?"
        );

        assertSourcePresent(results, "procedure-ch4-1-purchase-paper-no-lien.md");
    }

    // A2 — Verdana ELT, no lien, happy path
    @Test
    void a2_verdanaEltNoLien_shouldRetrieveEltConversionAndVerdanaProfile() {
        List<RetrievalResult> results = retrievalService.retrieveAndRerank(
                "A customer's vehicle is titled in Verdana (ELT state) with no lien. "
                + "Walk me through the Marion title process."
        );

        assertSourcePresent(results, "procedure-ch4-4-elt-conversion.md");
    }

    // A3 — Marion sales tax rate and basis rule
    @Test
    void a3_taxRateAndBasis_shouldRetrieveAdminRule7() {
        List<RetrievalResult> results = retrievalService.retrieveAndRerank(
                "What is Marion's current vehicle sales tax rate and how is the taxable value "
                + "determined for a purchase transaction?"
        );

        assertSourcePresent(results, "admin-rule-7-tax.md");
    }

    // A5 — Pembrook reciprocity (negative case — no agreement)
    @Test
    void a5_pembrookReciprocity_shouldRetrieveTaxReciprocitySchedule() {
        List<RetrievalResult> results = retrievalService.retrieveAndRerank(
                "Does Marion have a sales tax reciprocity agreement with Pembrook?"
        );

        assertSourcePresent(results, "tax-reciprocity-schedule.md");
    }

    // A6 — VIN inspection requirement and form
    @Test
    void a6_vinInspection_shouldRetrieveVinProcedureAndTr2() {
        List<RetrievalResult> results = retrievalService.retrieveAndRerank(
                "What VIN inspection is required for an out-of-state title transfer, "
                + "and which form documents it?"
        );

        boolean hasVinProcedure = results.stream()
                .anyMatch(r -> r.source() != null && r.source().contains("procedure-ch3-vin-inspection"));
        boolean hasTr2 = results.stream()
                .anyMatch(r -> r.source() != null && r.source().contains("form-tr2-instructions"));

        assertThat(hasVinProcedure || hasTr2)
                .as("Expected procedure-ch3-vin-inspection.md or form-tr2-instructions.md in top results")
                .isTrue();
    }

    private void assertSourcePresent(List<RetrievalResult> results, String expectedSource) {
        assertThat(results)
                .as("Expected source '%s' in top-%d results", expectedSource, results.size())
                .anyMatch(r -> r.source() != null && r.source().contains(expectedSource.replace(".md", "")));
    }
}
