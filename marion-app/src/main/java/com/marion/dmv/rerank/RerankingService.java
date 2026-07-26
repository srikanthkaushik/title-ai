package com.marion.dmv.rerank;

import com.marion.dmv.retrieval.RetrievalResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RerankingService {

    private static final Pattern SCORE_PATTERN = Pattern.compile("SCORE:\\s*(\\d+)");
    private static final String SYSTEM_PROMPT = """
            You are a relevance judge for a DMV title transfer assistant.
            Given a query and a candidate document excerpt, assess how relevant the excerpt is.

            First, reason through why the excerpt is or is not relevant to the query.
            Write your reasoning in 1-2 sentences.
            Then, on the FINAL line, output exactly: SCORE: N
            where N is an integer from 0 to 10 (10 = highly relevant, 0 = not relevant).
            Output nothing after the SCORE line.

            IMPORTANT: If the excerpt contains text indicating it is superseded, outdated, repealed,
            or replaced by a later version (e.g. "Superseded by", "This rule has been replaced",
            "Pre-2023", "Effective prior to"), reduce the score by 4 points minimum, even if the
            content is otherwise relevant. Examiners must use the current rule, not old versions.
            """;

    private final ChatModel chatModel;
    private final Timer rerankTimer;

    public RerankingService(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.rerankTimer = Timer.builder("marion.rerank")
                .description("LLM reranking time")
                .tag("node", "reranker")
                .register(meterRegistry);
    }

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates.size() <= topK) {
            return candidates;
        }

        record Scored(RetrievalResult result, int llmScore) {}
        List<Scored> scored = new ArrayList<>();

        for (RetrievalResult candidate : candidates) {
            int score = rerankTimer.record(() -> scoreCandidate(query, candidate));
            scored.add(new Scored(candidate, score));
        }

        scored.sort(Comparator.comparingInt(Scored::llmScore).reversed());

        return scored.stream()
                .limit(topK)
                .map(Scored::result)
                .toList();
    }

    private int scoreCandidate(String query, RetrievalResult candidate) {
        String userPrompt = String.format("""
                QUERY: %s

                DOCUMENT EXCERPT:
                %s

                How relevant is this excerpt to answering the query?
                """, query, candidate.text());

        try {
            String response = chatModel.chat(
                    List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt))
            ).aiMessage().text();

            Matcher m = SCORE_PATTERN.matcher(response);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            return -1;
        } catch (Exception e) {
            System.err.println(">>> Rerank scoring failed: " + e.getMessage());
            return -1;
        }
    }
}
