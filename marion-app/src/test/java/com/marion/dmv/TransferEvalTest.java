package com.marion.dmv;

import tools.jackson.databind.ObjectMapper;
import com.marion.dmv.transfer.TransferController;
import com.marion.dmv.transfer.TransferRequest;
import com.marion.dmv.transfer.TransferResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * LLM-as-judge transfer eval. Requires live Ollama + pgvector and ingested corpus.
 * MethodName ordering puts simpler tax tests (a4*) before brand/exception tests (b*, f*)
 * so the model hasn't processed many complex scenarios before the arithmetic checks.
 *
 * Scoring: reason-before-verdict, SCORE: N on final line (0-10). -1 = unparseable.
 */
@ActiveProfiles("eval")
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class TransferEvalTest {

    private static final Pattern SCORE_PATTERN = Pattern.compile("SCORE:\\s*(\\d+)");

    private static final String JUDGE_SYSTEM = """
            You are an evaluator assessing the quality of a Marion DMV title transfer assistant response.

            Evaluate the response on accuracy (correct documents, fees, tax, exception routing),
            completeness (nothing important omitted), and clarity (examiner can act on it immediately).

            First, reason through the response quality in 2-3 sentences.
            Then, on the FINAL line, output exactly: SCORE: N
            where N is an integer from 0 to 10.
            10 = fully correct, complete, and clear. 0 = wrong or harmful. 7+ = acceptable for production.
            """;

    @Autowired
    private TransferController transferController;

    @Autowired
    private ChatModel chatModel;  // judge — same provider as answer (Ollama qwen2.5:7b)

    @Autowired
    private ObjectMapper objectMapper;

    // A4 — Crestwood reciprocity credit exceeds Marion tax → $0 additional
    @Test
    void a4_crestwoodReciprocity_zeroAdditionalTax() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "The customer paid 6% sales tax in Crestwood on a $15,000 vehicle. " +
                "Marion's rate is 5.5%. How much additional tax is owed?",
                null, "Crestwood", "Marion County", "PURCHASE"
        ));

        // Deterministic: reciprocity credit (6% × $15k = $900) > Marion tax (5.5% × $15k = $825) → $0
        assertThat(response.supervisorReferral()).isFalse();
        assertThat(response.taxOwed()).isNotNull();
        assertThat(response.taxOwed()).isCloseTo(0.0, within(0.01));

        // LLM-as-judge
        int score = judge(
                "What additional sales tax is owed when customer paid 6% in Crestwood on a $15,000 vehicle?",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for A4 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // A4b — Pembrook, no reciprocity → full Marion tax (5.5% × $20,000 = $1,100)
    @Test
    void a4b_pembrookNoReciprocity_fullMarionTax() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "The customer purchased a vehicle for $20,000 and is transferring a Pembrook title into Marion. " +
                "No tax was collected by Pembrook on this transaction. How much Marion sales tax is owed?",
                "1PMB0000001000001", "Pembrook", "Marion County", "PURCHASE"
        ));

        // Pembrook has NO reciprocity → full 5.5%: 0.055 × 20000 = 1100.00
        assertThat(response.supervisorReferral()).isFalse();
        assertThat(response.taxOwed())
                .as("Pembrook no-reciprocity: full Marion tax on $20k should be $1,100")
                .isNotNull()
                .isCloseTo(1100.0, within(0.01));

        int score = judge(
                "Customer paid $0 tax in Pembrook on a $20,000 vehicle. Pembrook has no reciprocity agreement. " +
                "Marion rate is 5.5%. What is the Marion tax owed? Correct answer: $1,100.00 exactly.",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for A4b (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // A4c — Halloway, partial reciprocity credit → non-zero additional tax
    // Marion tax: 5.5% × $18,000 = $990. Halloway rate: 4.5% × $18,000 = $810 credit.
    // Additional owed: $990 - $810 = $180.
    @Test
    void a4c_hallowayPartialCredit_additionalTaxOwed() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "The customer paid 4.5% sales tax in Halloway on an $18,000 vehicle purchase. " +
                "Marion's rate is 5.5%. How much additional Marion sales tax is owed?",
                "1HAL0000001000002", "Halloway", "Marion County", "PURCHASE"
        ));

        // Halloway rate 4.5%: credit = min(4.5% × $18k = $810, 5.5% × $18k = $990) = $810
        // Additional = $990 - $810 = $180
        assertThat(response.supervisorReferral()).isFalse();
        assertThat(response.taxOwed())
                .as("Halloway partial credit: $990 Marion tax - $810 credit = $180 additional")
                .isNotNull()
                .isCloseTo(180.0, within(0.01));

        int score = judge(
                "Customer paid 4.5% in Halloway on $18,000. Marion rate 5.5%. Halloway has a reciprocity agreement. " +
                "Formula: credit = min(Halloway tax paid, Marion tax due) = min($810, $990) = $810. " +
                "Additional owed = $990 - $810 = $180. Correct answer: $180.00.",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for A4c (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // A4d — Verdana, partial credit, close rates (5% < 5.5%)
    // Marion tax: 5.5% × $20,000 = $1,100. Verdana rate: 5% × $20,000 = $1,000 credit.
    // Additional owed: $1,100 - $1,000 = $100.
    @Test
    void a4d_verdanaPartialCredit_additionalTaxOwed() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "The customer paid 5% sales tax in Verdana on a $20,000 vehicle purchase. " +
                "Marion's rate is 5.5%. How much additional Marion sales tax is owed?",
                "1VRD0000001000001", "Verdana", "Marion County", "PURCHASE"
        ));

        // Verdana rate 5%: credit = min(5% × $20k = $1,000, 5.5% × $20k = $1,100) = $1,000
        // Additional = $1,100 - $1,000 = $100
        assertThat(response.supervisorReferral()).isFalse();
        assertThat(response.taxOwed())
                .as("Verdana partial credit: $1,100 Marion tax - $1,000 credit = $100 additional")
                .isNotNull()
                .isCloseTo(100.0, within(0.01));
    }

    // F1 — Active paper lien → supervisor referral, no checklist, taxOwed=null
    @Test
    void f1_activePaperLien_supervisorReferral() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer presents a Crestwood title showing an unreleased lien held by " +
                "Midwest Auto Finance. What does the examiner do?",
                "1CST0000001000003", "Crestwood", "Marion County", "PURCHASE"
        ));

        // Deterministic: active lien must trigger referral
        assertThat(response.supervisorReferral())
                .as("Active lien must trigger supervisorReferral=true")
                .isTrue();
        assertThat(response.referralForm())
                .as("Referral form must be TR-10")
                .isEqualTo("TR-10");
        assertThat(response.checklist())
                .as("Checklist must be null on supervisor referral")
                .isNull();
        assertThat(response.taxOwed())
                .as("taxOwed must be null on supervisor referral")
                .isNull();
        assertThat(response.conditionalChecklist())
                .as("conditionalChecklist must be provided on referral")
                .isNotNull()
                .isNotEmpty();

        // LLM-as-judge
        int score = judge("What does the examiner do when a Crestwood title has an active lien?",
                objectMapper.writeValueAsString(response));
        assertThat(score)
                .as("Judge score for F1 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // B1 — Halloway has no emissions program; Marion emissions rules still apply
    @Test
    void b1_hallowayNoEmissions_marionRulesApply() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A vehicle was previously titled in Halloway, which has no emissions testing program. " +
                "Does the customer still need an emissions test in Marion for registration in Marion County?",
                null, "Halloway", "Marion County", "PURCHASE"
        ));

        // Deterministic: reasoning and response should indicate Marion emissions rules govern,
        // not Halloway's program. No supervisor referral expected for a clean title.
        assertThat(response.supervisorReferral()).isFalse();

        // LLM-as-judge: specifically testing that the answer does NOT say "exempt because Halloway
        // has no emissions program" and DOES say Marion's rules require testing for metro county
        int score = judge(
                "Does a Halloway-titled vehicle need Marion emissions testing if registering in Marion County?",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for B1 distractor test (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // B2 — Verdana "Rebuilt" → Marion "Rebuilt" (NOT "Reconstructed")
    // The dangerous distractor: Halloway also uses "Rebuilt" but maps to Marion "Reconstructed".
    // VIN 1VRD0000001000003 has brand=Rebuilt in the DB so MCP returns it as authoritative data,
    // making STEP 1 reliable. Requires MCP server running (port 8090); without it, qwen2.5:7b
    // occasionally misses the brand keyword scan and returns supervisorReferral=false (known flaky).
    @Test
    void b2_verdanaRebuiltBrand_supervisorReferralWithCorrectMarionBrand() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "What is the examiner required to do when a customer presents a Verdana vehicle title " +
                "that carries a 'Rebuilt' brand stamp? Which Marion brand equivalent applies?",
                "1VRD0000001000003", "Verdana", "Marion County", "PURCHASE"
        ));

        assertThat(response.supervisorReferral())
                .as("Branded title (Verdana 'Rebuilt') must trigger supervisorReferral=true")
                .isTrue();
        assertThat(response.referralForm()).isEqualTo("TR-10");
        assertThat(response.checklist()).isNull();

        // Judge specifically checks: Verdana "Rebuilt" → Marion "Rebuilt", not "Reconstructed"
        String serialized = objectMapper.writeValueAsString(response);
        int score = judge(
                "For a Verdana-titled vehicle with brand 'Rebuilt': what Marion brand applies and what does the examiner do? " +
                "Verdana 'Rebuilt' maps to Marion 'Rebuilt'. Halloway 'Rebuilt' maps to Marion 'Reconstructed' — these are different. " +
                "Score 10 if the response triggers supervisor referral AND states the Marion brand is 'Rebuilt'. " +
                "Score 4 or lower if it says 'Reconstructed' (wrong state's mapping) or skips the referral.",
                serialized
        );
        assertThat(score)
                .as("Judge score for B2 (expected >= 7, got %d). Response: %s", score, serialized)
                .isGreaterThanOrEqualTo(7);
    }

    // B3 — Halloway "Rebuilt" → Marion "Reconstructed" (NOT "Rebuilt" like Verdana's mapping)
    // Requires MCP server (port 8090) for VIN 1HAL0000001000001 brand=Rebuilt DB record.
    // Without MCP, qwen2.5:7b sometimes misses the brand trigger (known flaky, same root as B2).
    @Test
    void b3_hallowayRebuiltBrand_supervisorReferralWithDifferentMarionBrand() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer presents a Halloway title with the brand 'Rebuilt'. " +
                "What brand should appear on the Marion title?",
                "1HAL0000001000001", "Halloway", "Marion County", "RELOCATION"
        ));

        assertThat(response.supervisorReferral())
                .as("Branded title must trigger supervisorReferral=true")
                .isTrue();
        assertThat(response.referralForm()).isEqualTo("TR-10");

        // Judge specifically checks: Halloway "Rebuilt" → Marion "Reconstructed", not "Rebuilt"
        String serialized = objectMapper.writeValueAsString(response);
        int score = judge(
                "For a Halloway-titled vehicle with brand 'Rebuilt': what Marion brand applies? " +
                "Halloway 'Rebuilt' maps to Marion 'Reconstructed' (NOT 'Rebuilt'). " +
                "Verdana 'Rebuilt' maps to Marion 'Rebuilt' — but this is a Halloway title, not Verdana. " +
                "Score 10 if the response correctly identifies the Marion brand as 'Reconstructed'. " +
                "Score 4 or lower if it says 'Rebuilt' (which would be applying Verdana's mapping by mistake).",
                serialized
        );
        assertThat(score)
                .as("Judge score for B3 (expected >= 7, got %d). Response: %s", score, serialized)
                .isGreaterThanOrEqualTo(7);
    }

    // F2 — Verdana ELT with active lien → supervisor referral; lien must be released electronically
    @Test
    void f2_verdanaEltActiveLien_supervisorReferral() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer's vehicle is titled in Verdana as an ELT record and the record shows " +
                "an active lien. What is the process?",
                "1VRD0000001000002", "Verdana", "Marion County", "RELOCATION"
        ));

        assertThat(response.supervisorReferral())
                .as("ELT active lien must trigger supervisorReferral=true")
                .isTrue();
        assertThat(response.referralForm()).isEqualTo("TR-10");
        assertThat(response.checklist()).isNull();
        assertThat(response.taxOwed()).isNull();
        assertThat(response.conditionalChecklist()).isNotNull().isNotEmpty();

        int score = judge(
                "What is the process for a Verdana ELT-titled vehicle with an active lien?",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for F2 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // F3 — Halloway "Junk" brand → supervisor referral; Halloway "Junk" = Marion "Salvage"
    @Test
    void f3_hallowayJunkBrand_supervisorReferralWithSalvageEquivalent() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer presents a Halloway paper title with the brand 'Junk'. " +
                "What happens and what brand appears on the Marion title?",
                null, "Halloway", "Marion County", "PURCHASE"
        ));

        assertThat(response.supervisorReferral())
                .as("Branded title (Halloway 'Junk') must trigger supervisorReferral=true")
                .isTrue();
        assertThat(response.referralForm()).isEqualTo("TR-10");
        assertThat(response.checklist()).isNull();

        // Halloway "Junk" → Marion "Salvage" per brand equivalency guide
        int score = judge(
                "For a Halloway-titled vehicle with brand 'Junk': what does the examiner do and what Marion brand applies? " +
                "Halloway 'Junk' maps to Marion 'Salvage'. A supervisor referral (TR-10) is required. " +
                "Score 10 if the response identifies supervisor referral AND states the Marion brand is 'Salvage'. " +
                "Score lower if the Marion brand is omitted or wrong.",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for F3 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // C1 — Out-of-scope: commercial trailer fee (corpus covers passenger vehicles only)
    // The model should acknowledge it cannot find this information rather than inventing a fee.
    @Test
    void c1_commercialTrailerFee_outOfScope() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "What is the title transfer fee for a commercial trailer over 10,000 lbs in Marion?",
                null, null, null, null
        ));

        int score = judge(
                "The question asks for a commercial trailer title fee. Marion's corpus only covers " +
                "passenger vehicles. The correct response is to acknowledge this information is not " +
                "available in the corpus. Score 10 if the reasoning clearly states the information " +
                "is not found or out of scope. Score 2 if the response invents a specific dollar " +
                "amount for commercial trailers without acknowledging the limitation.",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for C1 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // C2 — Out-of-scope: Verdana DMV address (external contact info; not in corpus)
    // The corpus covers Marion procedures only; no external DMV addresses are documented.
    @Test
    void c2_verdanaDmvAddress_outOfScope() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "What is the address of the Verdana DMV title division? " +
                "The customer needs to contact Verdana to request an ELT release.",
                null, "Verdana", null, null
        ));

        int score = judge(
                "The question asks for the Verdana DMV's street address. This information is not " +
                "in the Marion DMV corpus — the corpus covers Marion procedures only, not external " +
                "contact information for other states' DMVs. " +
                "Score 10 if the response states the address is not available in the provided context " +
                "and directs the customer to contact Verdana DMV directly. " +
                "Score 2 if the response fabricates a specific street address for the Verdana DMV.",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for C2 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // D1 — Superseded fee schedule: current title fee is $25 (not $20); current tax rate is 5.5% (not 4.5%).
    // The superseded Admin Rule 9 (pre-2023) has title fee $20 and tax rate 4.5%.
    // Using Pembrook (no reciprocity, no retrieval distraction): $10,000 × 5.5% = $550 (current),
    // vs. $10,000 × 4.5% = $450 (superseded). Also checks title fee: current $25, superseded $20.
    @Test
    void d1_currentFeeSchedule_notSupersededVersion() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer purchased a vehicle for $10,000 from a Pembrook seller. " +
                "Pembrook has no reciprocity agreement with Marion. " +
                "What is the Marion sales tax owed and what is the title transfer fee?",
                null, "Pembrook", "Marion County", "PURCHASE"
        ));

        assertThat(response.supervisorReferral()).isFalse();
        // Current tax rate 5.5%: $10,000 × 5.5% = $550.
        // Superseded rate 4.5% would give $450 — wrong version.
        assertThat(response.taxOwed())
                .as("Current Marion rate 5.5%: $10k × 5.5% = $550; superseded 4.5% would give $450")
                .isNotNull()
                .isCloseTo(550.0, within(0.01));
        // Current title fee $25; superseded was $20.
        assertThat(response.fees()).isNotNull();
        double titleFee = ((Number) response.fees().get("titleFee")).doubleValue();
        assertThat(titleFee)
                .as("Current title fee is $25.00; superseded fee schedule had $20.00")
                .isCloseTo(25.0, within(0.01));
    }

    // F4 — Unrecognized origin state → supervisor referral
    // Westbrook is not in Marion's recognized-state list (Verdana, Crestwood, Halloway, Pembrook).
    @Test
    void f4_unrecognizedOriginState_supervisorReferral() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer is presenting a vehicle title from the state of Westbrook. " +
                "Westbrook is not one of Marion's recognized origin states. " +
                "What does the examiner do?",
                null, "Westbrook", "Marion County", "PURCHASE"
        ));

        assertThat(response.supervisorReferral())
                .as("Unrecognized origin state (Westbrook) must trigger supervisorReferral=true")
                .isTrue();
        assertThat(response.referralForm())
                .as("Referral form for unrecognized state must be TR-10")
                .isEqualTo("TR-10");
        assertThat(response.checklist())
                .as("Checklist must be null when supervisor referral is triggered")
                .isNull();

        int score = judge(
                "A customer presents a title from Westbrook, an unrecognized origin state not in " +
                "Marion's recognized-state list. What does the examiner do? " +
                "Correct answer: supervisor referral (Form TR-10) because the state is not recognized. " +
                "Score 10 if the response triggers supervisor referral and cites the unrecognized state. " +
                "Score 2 if the response attempts to process the transfer normally.",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for F4 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // D2 — 2003 vehicle in Marion County (metro): REQUIRED under current 25-year rule.
    // Under superseded 20-year rule a 23-year-old vehicle would have been exempt — this catches
    // the system using the wrong version of Admin Rule 2.4.
    // Deterministic-only: checklist must include an emissions item. Judge replaced by checklist
    // assertion because qwen2.5:7b judges a correct answer as wrong when it notes "current year 2023".
    @Test
    void d2_2003VehicleMetroCounty_emissionsRequiredUnderCurrentRule() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer is registering a 2003 model year vehicle in Marion County. " +
                "The current year is 2026. Is emissions testing required?",
                null, "Crestwood", "Marion County", "PURCHASE"
        ));

        assertThat(response.supervisorReferral()).isFalse();

        // 2003 is 23 years old in 2026; current rule exempts at ≥25 years → REQUIRED.
        // Superseded rule exempted at 20 years → would incorrectly omit emissions from checklist.
        assertThat(response.checklist())
                .as("Emissions inspection must appear in checklist: 2003 vehicle (23yr) in metro county is under 25-year exemption threshold")
                .isNotNull()
                .anyMatch(item -> item.toLowerCase().contains("emission"));
    }

    // R1 — Crestwood RELOCATION: checklist must require proof of Marion residency; no bill of sale
    @Test
    void r1_crestwoodRelocation_residencyDocumentsRequired() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer is relocating from Crestwood to Marion. " +
                "Their vehicle is titled in Crestwood (paper title, no lien). " +
                "What documents are required for the RELOCATION transfer?",
                "1CST0000001000001", "Crestwood", "Marion County", "RELOCATION"
        ));

        assertThat(response.supervisorReferral()).isFalse();

        int score = judge(
                "For a RELOCATION transfer (owner moved to Marion; vehicle already owned, not purchased): " +
                "the checklist must include proof of Marion residency (e.g., lease agreement, utility bill). " +
                "A bill of sale is NOT required — there is no purchase transaction. " +
                "Score 10 if proof of residency is listed AND bill of sale is absent. " +
                "Score 5 if residency document is present but bill of sale also appears (incorrect). " +
                "Score 2 if residency document is absent entirely.",
                objectMapper.writeValueAsString(response)
        );
        assertThat(score)
                .as("Judge score for R1 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // R2 — Verdana ELT RELOCATION: tax basis = NADA ($22,000); Verdana 5% vs Marion 5.5% → $110 owed
    // Marion = 5.5% × $22,000 = $1,210. Credit = min(5% × $22,000, $1,210) = min($1,100, $1,210) = $1,100.
    // Additional owed = $1,210 − $1,100 = $110.
    @Test
    void r2_verdanaEltRelocation_taxOnNadaBasis() throws Exception {
        TransferResponse response = callTransfer(new TransferRequest(
                "A customer relocated from Verdana to Marion. Their vehicle is titled in Verdana " +
                "(ELT, no lien). The NADA clean retail value is $22,000. They paid 5% sales tax " +
                "in Verdana at the time of original purchase. How much additional Marion sales tax is owed?",
                "1VRD0000001000001", "Verdana", "Marion County", "RELOCATION"
        ));

        assertThat(response.supervisorReferral()).isFalse();
        assertThat(response.taxOwed())
                .as("RELOCATION tax: Marion $1,210 − Verdana credit $1,100 = $110 owed")
                .isNotNull()
                .isCloseTo(110.00, within(0.01));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TransferResponse callTransfer(TransferRequest request) {
        return transferController.query(request).block();
    }

    private int judge(String question, String response) {
        String prompt = String.format("""
                QUESTION ASKED: %s

                ASSISTANT RESPONSE:
                %s

                Evaluate the response quality.
                """, question, response);

        try {
            String judgeOutput = chatModel.chat(
                    List.of(SystemMessage.from(JUDGE_SYSTEM), UserMessage.from(prompt))
            ).aiMessage().text();

            Matcher m = SCORE_PATTERN.matcher(judgeOutput);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            System.err.println("Judge call failed: " + e.getMessage());
        }
        return -1;
    }
}
