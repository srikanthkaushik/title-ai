package com.marion.dmv.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Value("${llm.provider:anthropic}")
    private String provider;

    @Value("${anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${anthropic.model:claude-sonnet-4-6}")
    private String anthropicModel;

    @Value("${anthropic.max-tokens:4096}")
    private int anthropicMaxTokens;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.chat-model:qwen2.5:7b}")
    private String ollamaChatModel;

    @Value("${ollama.timeout-minutes:5}")
    private int ollamaTimeoutMinutes;

    @Bean
    public ChatModel chatModel() {
        if ("ollama".equalsIgnoreCase(provider)) {
            System.out.println(">>> LLM provider: OLLAMA (model: " + ollamaChatModel + ")");
            return OllamaChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(ollamaChatModel)
                    .timeout(Duration.ofMinutes(ollamaTimeoutMinutes))
                    .build();
        }
        System.out.println(">>> LLM provider: ANTHROPIC (model: " + anthropicModel + ")");
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey)
                .modelName(anthropicModel)
                .maxTokens(anthropicMaxTokens)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        if ("ollama".equalsIgnoreCase(provider)) {
            return OllamaStreamingChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(ollamaChatModel)
                    .timeout(Duration.ofMinutes(ollamaTimeoutMinutes))
                    .build();
        }
        return AnthropicStreamingChatModel.builder()
                .apiKey(anthropicApiKey)
                .modelName(anthropicModel)
                .maxTokens(anthropicMaxTokens)
                .build();
    }
}
