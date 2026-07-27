package com.marion.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InspectionStationTools {

    private final JdbcTemplate jdbc;

    public InspectionStationTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @McpTool(description = """
            Find authorized inspection stations in a Marion county.
            Returns station name, address, inspection types (VIN, EMISSIONS, or BOTH), phone, and notes.
            Metro counties (Marion County, Riverside County, Capital County) have stations.
            Rural counties (Dunmore County, Alderton County, etc.) return empty list — VIN inspections
            can still be performed by licensed inspectors or law enforcement, not at a fixed station.
            Use inspection_type filter: VIN, EMISSIONS, or BOTH.
            """)
    public List<Map<String, Object>> check_inspection_stations(
            @McpToolParam(description = "Marion county name, e.g., Marion County, Riverside County") String county,
            @McpToolParam(description = "Inspection type needed: VIN, EMISSIONS, or BOTH") String inspectionType) {

        String typeFilter = switch (inspectionType.toUpperCase()) {
            case "EMISSIONS" -> "inspection_types IN ('EMISSIONS', 'BOTH')";
            case "BOTH" -> "inspection_types = 'BOTH'";
            default -> "inspection_types IN ('VIN', 'BOTH')"; // VIN
        };

        return jdbc.queryForList(
                "SELECT station_name, address, inspection_types, phone, notes " +
                "FROM inspection_stations " +
                "WHERE LOWER(county) = LOWER(?) AND station_name IS NOT NULL AND " + typeFilter +
                " ORDER BY station_name",
                county
        );
    }

    @McpTool(description = """
            Check whether a Marion county requires emissions testing.
            Returns county_type (METRO/RURAL) and emissions_required (true/false).
            Metro counties: Marion County, Riverside County, Capital County.
            Rural counties are exempt from emissions regardless of vehicle age.
            """)
    public Map<String, Object> check_county_emissions(
            @McpToolParam(description = "Marion county name, e.g., Marion County, Dunmore County") String county) {

        try {
            return jdbc.queryForMap(
                    """
                    SELECT county_name, county_type, emissions_required, notes
                    FROM marion_counties WHERE LOWER(county_name) = LOWER(?)
                    """,
                    county
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of(
                    "status", "NOT_FOUND",
                    "county", county,
                    "note", "County not found. Treat as rural — no emissions required."
            );
        }
    }
}
