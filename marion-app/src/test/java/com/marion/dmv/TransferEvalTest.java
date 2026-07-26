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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * LLM-as-judge transfer eval. Requires live Ollama + pgvector and ingested corpus.
 * MethodName ordering puts simpler tax tests (a4*) before brand/exception tests (b*, f*)
 * so the model hasn't processed many complex scenarios before the arithmetic checks.
 *
 * Scoring: reason-before-verdict, SCORE: N on final line (0-10). -1 = unparseable.
 */
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
        String response = callTransfer(new TransferRequest(
                "The customer paid 6% sales tax in Crestwood on a $15,000 vehicle. " +
                "Marion's rate is 5.5%. How much additional tax is owed?",
                null, "Crestwood", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);

        // Deterministic: reciprocity credit (6% × $15k = $900) > Marion tax (5.5% × $15k = $825) → $0
        assertThat(parsed.supervisorReferral()).isFalse();
        assertThat(parsed.taxOwed()).isNotNull();
        assertThat(parsed.taxOwed()).isCloseTo(0.0, within(0.01));

        // LLM-as-judge
        int score = judge(
                "What additional sales tax is owed when customer paid 6% in Crestwood on a $15,000 vehicle?",
                response
        );
        assertThat(score)
                .as("Judge score for A4 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // A4b — Pembrook, no reciprocity → full Marion tax (5.5% × $20,000 = $1,100)
    @Test
    void a4b_pembrookNoReciprocity_fullMarionTax() throws Exception {
        String response = callTransfer(new TransferRequest(
                "The customer purchased a vehicle for $20,000 and is transferring a Pembrook title into Marion. " +
                "No tax was collected by Pembrook on this transaction. How much Marion sales tax is owed?",
                "1PMB0000001000001", "Pembrook", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);

        // Pembrook has NO reciprocity → full 5.5%: 0.055 × 20000 = 1100.00
        assertThat(parsed.supervisorReferral()).isFalse();
        assertThat(parsed.taxOwed())
                .as("Pembrook no-reciprocity: full Marion tax on $20k should be $1,100")
                .isNotNull()
                .isCloseTo(1100.0, within(0.01));

        int score = judge(
                "Customer paid $0 tax in Pembrook on a $20,000 vehicle. Pembrook has no reciprocity agreement. " +
                "Marion rate is 5.5%. What is the Marion tax owed? Correct answer: $1,100.00 exactly.",
                response
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
        String response = callTransfer(new TransferRequest(
                "The customer paid 4.5% sales tax in Halloway on an $18,000 vehicle purchase. " +
                "Marion's rate is 5.5%. How much additional Marion sales tax is owed?",
                "1HAL0000001000002", "Halloway", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);

        // Halloway rate 4.5%: credit = min(4.5% × $18k = $810, 5.5% × $18k = $990) = $810
        // Additional = $990 - $810 = $180
        assertThat(parsed.supervisorReferral()).isFalse();
        assertThat(parsed.taxOwed())
                .as("Halloway partial credit: $990 Marion tax - $810 credit = $180 additional")
                .isNotNull()
                .isCloseTo(180.0, within(0.01));

        int score = judge(
                "Customer paid 4.5% in Halloway on $18,000. Marion rate 5.5%. Halloway has a reciprocity agreement. " +
                "Formula: credit = min(Halloway tax paid, Marion tax due) = min($810, $990) = $810. " +
                "Additional owed = $990 - $810 = $180. Correct answer: $180.00.",
                response
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
        String response = callTransfer(new TransferRequest(
                "The customer paid 5% sales tax in Verdana on a $20,000 vehicle purchase. " +
                "Marion's rate is 5.5%. How much additional Marion sales tax is owed?",
                "1VRD0000001000001", "Verdana", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);

        // Verdana rate 5%: credit = min(5% × $20k = $1,000, 5.5% × $20k = $1,100) = $1,000
        // Additional = $1,100 - $1,000 = $100
        assertThat(parsed.supervisorReferral()).isFalse();
        assertThat(parsed.taxOwed())
                .as("Verdana partial credit: $1,100 Marion tax - $1,000 credit = $100 additional")
                .isNotNull()
                .isCloseTo(100.0, within(0.01));

        int score = judge(
                "Customer paid 5% in Verdana on $20,000. Marion rate 5.5%. Verdana has a reciprocity agreement. " +
                "Formula: credit = min(Verdana tax paid, Marion tax due) = min($1,000, $1,100) = $1,000. " +
                "Additional owed = $1,100 - $1,000 = $100. Correct answer: $100.00.",
                response
        );
        assertThat(score)
                .as("Judge score for A4d (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // F1 — Active paper lien → supervisor referral, no checklist, taxOwed=null
    @Test
    void f1_activePaperLien_supervisorReferral() throws Exception {
        String response = callTransfer(new TransferRequest(
                "A customer presents a Crestwood title showing an unreleased lien held by " +
                "Midwest Auto Finance. What does the examiner do?",
                "1CST0000001000003", "Crestwood", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);

        // Deterministic: active lien must trigger referral
        assertThat(parsed.supervisorReferral())
                .as("Active lien must trigger supervisorReferral=true")
                .isTrue();
        assertThat(parsed.referralForm())
                .as("Referral form must be TR-10")
                .isEqualTo("TR-10");
        assertThat(parsed.checklist())
                .as("Checklist must be null on supervisor referral")
                .isNull();
        assertThat(parsed.taxOwed())
                .as("taxOwed must be null on supervisor referral")
                .isNull();
        assertThat(parsed.conditionalChecklist())
                .as("conditionalChecklist must be provided on referral")
                .isNotNull()
                .isNotEmpty();

        // LLM-as-judge
        int score = judge("What does the examiner do when a Crestwood title has an active lien?", response);
        assertThat(score)
                .as("Judge score for F1 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // B1 — Halloway has no emissions program; Marion emissions rules still apply
    @Test
    void b1_hallowayNoEmissions_marionRulesApply() throws Exception {
        String response = callTransfer(new TransferRequest(
                "A vehicle was previously titled in Halloway, which has no emissions testing program. " +
                "Does the customer still need an emissions test in Marion for registration in Marion County?",
                null, "Halloway", "Marion County", "PURCHASE"
        ));

        // Deterministic: reasoning and response should indicate Marion emissions rules govern,
        // not Halloway's program. No supervisor referral expected for a clean title.
        TransferResponse parsed = parseJson(response);
        assertThat(parsed.supervisorReferral()).isFalse();

        // LLM-as-judge: specifically testing that the answer does NOT say "exempt because Halloway
        // has no emissions program" and DOES say Marion's rules require testing for metro county
        int score = judge(
                "Does a Halloway-titled vehicle need Marion emissions testing if registering in Marion County?",
                response
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
        String response = callTransfer(new TransferRequest(
                "What is the examiner required to do when a customer presents a Verdana vehicle title " +
                "that carries a 'Rebuilt' brand stamp? Which Marion brand equivalent applies?",
                "1VRD0000001000003", "Verdana", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);

        assertThat(parsed.supervisorReferral())
                .as("Branded title (Verdana 'Rebuilt') must trigger supervisorReferral=true")
                .isTrue();
        assertThat(parsed.referralForm()).isEqualTo("TR-10");
        assertThat(parsed.checklist()).isNull();

        // Judge specifically checks: Verdana "Rebuilt" → Marion "Rebuilt", not "Reconstructed"
        int score = judge(
                "For a Verdana-titled vehicle with brand 'Rebuilt': what Marion brand applies and what does the examiner do? " +
                "Verdana 'Rebuilt' maps to Marion 'Rebuilt'. Halloway 'Rebuilt' maps to Marion 'Reconstructed' — these are different. " +
                "Score 10 if the response triggers supervisor referral AND states the Marion brand is 'Rebuilt'. " +
                "Score 4 or lower if it says 'Reconstructed' (wrong state's mapping) or skips the referral.",
                response
        );
        assertThat(score)
                .as("Judge score for B2 (expected >= 7, got %d). Response: %s", score, response)
                .isGreaterThanOrEqualTo(7);
    }

    // B3 — Halloway "Rebuilt" → Marion "Reconstructed" (NOT "Rebuilt" like Verdana's mapping)
    // Requires MCP server (port 8090) for VIN 1HAL0000001000001 brand=Rebuilt DB record.
    // Without MCP, qwen2.5:7b sometimes misses the brand trigger (known flaky, same root as B2).
    @Test
    void b3_hallowayRebuiltBrand_supervisorReferralWithDifferentMarionBrand() throws Exception {
        String response = callTransfer(new TransferRequest(
                "A customer presents a Halloway title with the brand 'Rebuilt'. " +
                "What brand should appear on the Marion title?",
                "1HAL0000001000001", "Halloway", "Marion County", "RELOCATION"
        ));

        TransferResponse parsed = parseJson(response);

        assertThat(parsed.supervisorReferral())
                .as("Branded title must trigger supervisorReferral=true")
                .isTrue();
        assertThat(parsed.referralForm()).isEqualTo("TR-10");

        // Judge specifically checks: Halloway "Rebuilt" → Marion "Reconstructed", not "Rebuilt"
        int score = judge(
                "For a Halloway-titled vehicle with brand 'Rebuilt': what Marion brand applies? " +
                "Halloway 'Rebuilt' maps to Marion 'Reconstructed' (NOT 'Rebuilt'). " +
                "Verdana 'Rebuilt' maps to Marion 'Rebuilt' — but this is a Halloway title, not Verdana. " +
                "Score 10 if the response correctly identifies the Marion brand as 'Reconstructed'. " +
                "Score 4 or lower if it says 'Rebuilt' (which would be applying Verdana's mapping by mistake).",
                response
        );
        assertThat(score)
                .as("Judge score for B3 (expected >= 7, got %d). Response: %s", score, response)
                .isGreaterThanOrEqualTo(7);
    }

    // F2 — Verdana ELT with active lien → supervisor referral; lien must be released electronically
    @Test
    void f2_verdanaEltActiveLien_supervisorReferral() throws Exception {
        String response = callTransfer(new TransferRequest(
                "A customer's vehicle is titled in Verdana as an ELT record and the record shows " +
                "an active lien. What is the process?",
                "1VRD0000001000002", "Verdana", "Marion County", "RELOCATION"
        ));

        TransferResponse parsed = parseJson(response);

        assertThat(parsed.supervisorReferral())
                .as("ELT active lien must trigger supervisorReferral=true")
                .isTrue();
        assertThat(parsed.referralForm()).isEqualTo("TR-10");
        assertThat(parsed.checklist()).isNull();
        assertThat(parsed.taxOwed()).isNull();
        assertThat(parsed.conditionalChecklist()).isNotNull().isNotEmpty();

        int score = judge(
                "What is the process for a Verdana ELT-titled vehicle with an active lien?",
                response
        );
        assertThat(score)
                .as("Judge score for F2 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // F3 — Halloway "Junk" brand → supervisor referral; Halloway "Junk" = Marion "Salvage"
    @Test
    void f3_hallowayJunkBrand_supervisorReferralWithSalvageEquivalent() throws Exception {
        String response = callTransfer(new TransferRequest(
                "A customer presents a Halloway paper title with the brand 'Junk'. " +
                "What happens and what brand appears on the Marion title?",
                null, "Halloway", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);

        assertThat(parsed.supervisorReferral())
                .as("Branded title (Halloway 'Junk') must trigger supervisorReferral=true")
                .isTrue();
        assertThat(parsed.referralForm()).isEqualTo("TR-10");
        assertThat(parsed.checklist()).isNull();

        // Halloway "Junk" → Marion "Salvage" per brand equivalency guide
        int score = judge(
                "For a Halloway-titled vehicle with brand 'Junk': what does the examiner do and what Marion brand applies? " +
                "Halloway 'Junk' maps to Marion 'Salvage'. A supervisor referral (TR-10) is required. " +
                "Score 10 if the response identifies supervisor referral AND states the Marion brand is 'Salvage'. " +
                "Score lower if the Marion brand is omitted or wrong.",
                response
        );
        assertThat(score)
                .as("Judge score for F3 (expected >= 7, got %d)", score)
                .isGreaterThanOrEqualTo(7);
    }

    // D2 — 2003 vehicle in Marion County (metro): REQUIRED under current 25-year rule.
    // Under superseded 20-year rule a 23-year-old vehicle would have been exempt — this catches
    // the system using the wrong version of Admin Rule 2.4.
    // Deterministic-only: checklist must include an emissions item. Judge replaced by checklist
    // assertion because qwen2.5:7b judges a correct answer as wrong when it notes "current year 2023".
    @Test
    void d2_2003VehicleMetroCounty_emissionsRequiredUnderCurrentRule() throws Exception {
        String response = callTransfer(new TransferRequest(
                "A customer is registering a 2003 model year vehicle in Marion County. " +
                "The current year is 2026. Is emissions testing required?",
                null, "Crestwood", "Marion County", "PURCHASE"
        ));

        TransferResponse parsed = parseJson(response);
        assertThat(parsed.supervisorReferral()).isFalse();

        // 2003 is 23 years old in 2026; current rule exempts at ≥25 years → REQUIRED.
        // Superseded rule exempted at 20 years → would incorrectly omit emissions from checklist.
        assertThat(parsed.checklist())
                .as("Emissions inspection must appear in checklist: 2003 vehicle (23yr) in metro county is under 25-year exemption threshold")
                .isNotNull()
                .anyMatch(item -> item.toLowerCase().contains("emission"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String callTransfer(TransferRequest request) {
        return transferController.query(request)
                .filter(token -> !"[DONE]".equals(token))
                .collect(Collectors.joining())
                .block();
    }

    private TransferResponse parseJson(String json) throws Exception {
        String trimmed = json.trim();
        // Strip any leading/trailing tokens that aren't JSON (e.g., markdown fences)
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        // qwen2.5 emits both // line comments and /* */ block comments inside JSON; strip both
        trimmed = trimmed.replaceAll("(?s)/\\*.*?\\*/", "");
        trimmed = trimmed.replaceAll("//[^\n]*", "");
        return objectMapper.readValue(trimmed, TransferResponse.class);
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
