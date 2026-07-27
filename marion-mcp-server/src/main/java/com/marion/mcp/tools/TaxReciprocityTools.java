package com.marion.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TaxReciprocityTools {

    private final JdbcTemplate jdbc;

    public TaxReciprocityTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @McpTool(description = """
            Look up Marion's tax reciprocity agreement with an origin state.
            Returns: has_agreement (true/false), origin_rate_pct (null if no agreement),
            and notes explaining the credit computation.
            Pembrook returns has_agreement=false and null rate — no credit applies.
            Marion's tax rate is 5.5%. Credit = min(tax_paid_in_origin, marion_tax_due).
            If credit >= marion_tax_due, additional tax owed is $0 (no refund issued).
            """)
    public Map<String, Object> lookup_tax_reciprocity(
            @McpToolParam(description = "Origin state name (e.g., Verdana, Crestwood, Halloway, Pembrook)") String originState) {

        try {
            return jdbc.queryForMap(
                    """
                    SELECT origin_state, has_agreement, origin_rate_pct, notes
                    FROM tax_reciprocity WHERE LOWER(origin_state) = LOWER(?)
                    """,
                    originState
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of(
                    "status", "NOT_FOUND",
                    "origin_state", originState,
                    "note", "Origin state not found in reciprocity table. Treat as no agreement."
            );
        }
    }
}
