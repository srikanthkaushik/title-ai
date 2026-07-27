package com.marion.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TitleLienTools {

    private final JdbcTemplate jdbc;

    public TitleLienTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @McpTool(description = """
            Look up a vehicle title record by VIN. Returns origin state, title form (PAPER/ELT/MIXED),
            lien status (NONE/RELEASED/ACTIVE), lienholder name, lien date, brand (if any),
            make, model, model year, body type, GVWR, odometer, insurance expiry, and notes.
            Returns null fields for missing data. Returns NOT_FOUND status if VIN is not in the database.
            """)
    public Map<String, Object> lookup_title_lien(
            @McpToolParam(description = "17-character Vehicle Identification Number") String vin) {

        try {
            return jdbc.queryForMap(
                    """
                    SELECT vin, origin_state, title_form, lien_status, lienholder_name,
                           lien_date, brand, make, model, model_year, body_type, gvwr_lbs,
                           odometer, insurance_expiry, notes
                    FROM vehicles WHERE vin = ?
                    """,
                    vin
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of("status", "NOT_FOUND", "vin", vin);
        }
    }

    @McpTool(description = """
            Decode a VIN to return make, model, model year, body type, and GVWR in pounds.
            Looks up the vehicles table; returns NOT_FOUND if VIN is not on record.
            This tool is for vehicle identification only — use lookup_title_lien for lien status.
            """)
    public Map<String, Object> decode_vin(
            @McpToolParam(description = "17-character Vehicle Identification Number") String vin) {

        try {
            return jdbc.queryForMap(
                    """
                    SELECT vin, make, model, model_year, body_type, gvwr_lbs
                    FROM vehicles WHERE vin = ?
                    """,
                    vin
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of("status", "NOT_FOUND", "vin", vin);
        }
    }
}
