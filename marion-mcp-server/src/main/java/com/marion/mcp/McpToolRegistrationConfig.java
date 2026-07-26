package com.marion.mcp;

import com.marion.mcp.tools.FeeScheduleTools;
import com.marion.mcp.tools.InspectionStationTools;
import com.marion.mcp.tools.TaxReciprocityTools;
import com.marion.mcp.tools.TitleLienTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolRegistrationConfig {

    @Bean
    public ToolCallbackProvider marionTools(
            TitleLienTools titleLienTools,
            TaxReciprocityTools taxReciprocityTools,
            FeeScheduleTools feeScheduleTools,
            InspectionStationTools inspectionStationTools) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(titleLienTools, taxReciprocityTools, feeScheduleTools, inspectionStationTools)
                .build();
    }
}
