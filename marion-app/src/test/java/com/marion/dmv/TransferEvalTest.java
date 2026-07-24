package com.marion.dmv;

import tools.jackson.databind.ObjectMapper;
import com.marion.dmv.transfer.TransferController;
import com.marion.dmv.transfer.TransferRequest;
import com.marion.dmv.transfer.TransferResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * LLM-as-judge transfer eval. Requires live LLM, Ollama + pgvector, and ingested corpus.
 * Judge always uses the configured ChatModel (TODO: pin judge to Anthropic when provider=ollama).
 *
 * Scoring: reason-before-verdict, SCORE: N on final line (0-10). -1 = unparseable.
 */
@Disabled("Integration test — requires live LLM + pgvector + ingested corpus")
@SpringBootTest
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
    private ChatModel chatModel;  // TODO: pin to Anthropic when provider=ollama (CLAUDE.md §design rules)

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
