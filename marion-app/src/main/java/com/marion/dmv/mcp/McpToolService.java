package com.marion.dmv.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin facade over the LC4j MCP client.
 *
 * The client is built lazily on first call so the app boots cleanly
 * even when the MCP server is down. All failures return Optional.empty()
 * — callers get graceful degradation, never a hard failure.
 */
@Service
public class McpToolService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpToolService.class);

    private final String mcpServerUrl;
    private volatile McpClient mcpClient;
    private final Object clientLock = new Object();

    public McpToolService(@Value("${mcp.server.url}") String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
    }

    public Optional<String> lookupTitleLien(String vin) {
        return callTool("lookup_title_lien", "{\"vin\":\"" + vin + "\"}");
    }

    public Optional<String> lookupTaxReciprocity(String originState) {
        return callTool("lookup_tax_reciprocity", "{\"originState\":\"" + originState + "\"}");
    }

    public Optional<String> lookupFees(String transferType, String county) {
        return callTool("lookup_fees",
                "{\"transferType\":\"" + transferType + "\",\"county\":\"" + county + "\"}");
    }

    public Optional<String> checkCountyEmissions(String county) {
        return callTool("check_county_emissions", "{\"county\":\"" + county + "\"}");
    }

    private Optional<String> callTool(String toolName, String argumentsJson) {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(argumentsJson)
                    .build();
            ToolExecutionResult result = getClient().executeTool(request);
            if (result.isError()) {
                log.warn("MCP tool {} returned error: {}", toolName, result.resultText());
                return Optional.empty();
            }
            return Optional.ofNullable(result.resultText());
        } catch (Exception e) {
            log.warn("MCP tool {} unavailable ({}): {}", toolName, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private McpClient getClient() {
        if (mcpClient == null) {
            synchronized (clientLock) {
                if (mcpClient == null) {
                    mcpClient = new DefaultMcpClient.Builder()
                            .transport(new StreamableHttpMcpTransport.Builder()
                                    .url(mcpServerUrl)
                                    .logRequests(false)
                                    .logResponses(false)
                                    .build())
                            .initializationTimeout(Duration.ofSeconds(5))
                            .toolExecutionTimeout(Duration.ofSeconds(10))
                            .autoHealthCheck(false)
                            .build();
                }
            }
        }
        return mcpClient;
    }

    @Override
    public void close() {
        if (mcpClient != null) {
            try {
                mcpClient.close();
            } catch (Exception ignored) {
            }
        }
    }
}
