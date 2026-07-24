package com.marion.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FeeScheduleTools {

    private final JdbcTemplate jdbc;

    public FeeScheduleTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool(description = """
            Return applicable DMV fees for an out-of-state title transfer.
            Always includes: title transfer ($25) and VIN inspection ($15).
            Conditionally includes: emissions ($35, paid to TESTING_STATION — not DMV) if county
            is metro (Marion County, Riverside County, Capital County) and vehicle model year
            is less than 25 years old from the current year.
            Registration fee: $45 for <= 5000 lbs GVWR, $65 for 5001-8500 lbs GVWR.
            Lien release fee ($5) only if TR-3 form is used (registered lender system).
            """)
    public List<Map<String, Object>> lookup_fees(
            @ToolParam(description = "Transfer type: PURCHASE or RELOCATION") String transferType,
            @ToolParam(description = "Registration county in Marion (e.g., Marion County, Dunmore County)") String county) {

        return jdbc.queryForList(
                """
                SELECT fee_code, description, amount, collected_by, notes
                FROM fee_schedule
                WHERE fee_code IN ('TITLE_TRANSFER', 'VIN_INSPECTION', 'REG_LIGHT', 'REG_HEAVY', 'EMISSIONS', 'LIEN_RELEASE')
                ORDER BY fee_code
                """
        );
    }

    @Tool(description = """
            Look up a single fee by its code. Known codes:
            TITLE_TRANSFER ($25), VIN_INSPECTION ($15), EMISSIONS ($35, TESTING_STATION),
            REG_LIGHT ($45, <= 5000 lbs), REG_HEAVY ($65, 5001-8500 lbs),
            LIEN_RELEASE ($5, TR-3 only), EXCEPTION_REVIEW ($0), DUPLICATE_TITLE ($10).
            """)
    public Map<String, Object> lookup_fee_by_code(
            @ToolParam(description = "Fee code, e.g., TITLE_TRANSFER, VIN_INSPECTION, EMISSIONS") String feeCode) {

        try {
            return jdbc.queryForMap(
                    "SELECT fee_code, description, amount, collected_by, notes FROM fee_schedule WHERE fee_code = ?",
                    feeCode.toUpperCase()
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of("status", "NOT_FOUND", "fee_code", feeCode);
        }
    }
}
