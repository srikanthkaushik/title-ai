# Marion DMV — System Architecture

## System Overview

```mermaid
graph TD
    Browser["Browser\nAngular 21 + Bootstrap 5\n:4200 (dev)"]

    subgraph marion-app ["marion-app — Spring WebFlux :8080"]
        PII["PiiGuardrailFilter\n@Order(-100)\nblocks SSN / card patterns"]
        TC["TransferController\nPOST /api/transfer/query"]
        TAC["TransferAgentController\nPOST /api/transfer/query/agent"]
        Graph["LangGraph4j Agent\nRETRIEVE → TOOL_FETCH → GENERATE"]
        Retrieval["RetrievalService\nhybrid vector + FTS + LLM rerank"]
        MCP_Client["McpToolService\nlazy DefaultMcpClient"]
        Parser["TransferResponseParser\nJackson 3, strips // and /* */ comments"]
    end

    subgraph marion-mcp-server ["marion-mcp-server — Spring MVC :8090"]
        Tools["@McpTool methods\nlookupTitleLien · lookupTaxReciprocity\nlookupFees · checkCountyEmissions\ncheckInspectionStations"]
    end

    subgraph Storage
        PG[("PostgreSQL + pgvector :5432\ndoc_embeddings · title_records\ntax_reciprocity · fee_schedule\ninspection_stations")]
    end

    subgraph LLM
        Ollama["Ollama :11434\nnomic-embed-text (768-dim)\nqwen2.5:7b"]
        Anthropic["Anthropic API\nclaude-sonnet-4-6\n(eval / llm.provider=anthropic)"]
    end

    Browser -->|"proxy /api →"| PII
    PII --> TC
    PII --> TAC
    TAC --> Graph
    Graph --> Retrieval
    Graph --> MCP_Client
    Graph -->|"chat()"| Ollama
    Graph -->|"chat()"| Anthropic
    TC --> Retrieval
    TC --> MCP_Client
    TC -->|"chat()"| Ollama
    TC -->|"chat()"| Anthropic
    Retrieval -->|"embed + vector search + FTS"| PG
    Retrieval -->|"rerank LLM call"| Ollama
    MCP_Client -->|"StreamableHttp POST /mcp"| Tools
    Tools -->|SQL| PG
    TC --> Parser
    TAC --> Parser
```

## Component Roles

| Component | Role |
|---|---|
| `PiiGuardrailFilter` | WebFlux `WebFilter` at `@Order(-100)`; regex-blocks SSN and card patterns on `/api/transfer/**`; returns 400 before the request reaches any controller |
| `TransferController` | Single-shot endpoint; retrieval + MCP tools + LLM in one blocking `Mono.fromCallable`; retries once on parse failure with specific Jackson error in prompt |
| `TransferAgentController` | Delegates to the compiled LangGraph4j graph; returns parsed `TransferResponse` |
| `TransferAgentGraph` | Defines RETRIEVE → TOOL_FETCH → GENERATE graph with conditional retry edge; each node timed via `marion.agent.node` Micrometer metric |
| `RetrievalService` | Hybrid retrieval: vector cosine + Postgres FTS; retrieves 3× target, reranks with LLM cross-encoder (reason-before-verdict, `SCORE:` pattern), returns top-N |
| `McpToolService` | Lazy `DefaultMcpClient` with double-checked locking; degrades gracefully if MCP server is unreachable |
| `TransferResponseParser` | Strips `//` and `/* */` comments from raw LLM output, extracts first `{…}` block, deserializes with Jackson 3 `FAIL_ON_UNKNOWN_PROPERTIES=false` |
| `GlobalExceptionHandler` | `@RestControllerAdvice`; IAE → 422 `PARSE_FAILED`, ISE → 500 `AGENT_ERROR` |
| MCP server tools | Five `@McpTool` methods backed by PostgreSQL; auto-registered by Spring AI 2.0 |

## Ports and Dependencies

```mermaid
graph LR
    UI["Angular UI\n:4200"] -->|"HTTP /api/*"| App["marion-app\n:8080"]
    App -->|"Streamable HTTP"| MCP["marion-mcp-server\n:8090"]
    App -->|"JDBC"| PG[("PostgreSQL\n:5432")]
    App -->|"HTTP"| Ollama["Ollama\n:11434"]
    MCP -->|"JDBC"| PG
```

## HTTP Endpoints

| Method | Path | Handler | Returns |
|---|---|---|---|
| `POST` | `/api/transfer/query` | `TransferController` | `TransferResponse` (JSON, 200) |
| `POST` | `/api/transfer/query/agent` | `TransferAgentController` | `TransferResponse` (JSON, 200) |
| `POST` | `/api/ingest/reset?confirm=true` | `IngestController` | wipes + reseeds corpus |
| `GET` | `/actuator/health` | Spring Boot Actuator | health status |
| `GET` | `/actuator/metrics/marion.agent.node` | Micrometer | node latency by tag |

---

# RAG Pipeline

```mermaid
flowchart TD
    Q["Examiner question"] --> Embed["Embed with\nnomic-embed-text\n768-dim"]

    Embed --> Vec["Vector search\npgvector cosine similarity\n(3× target count)"]
    Q --> FTS["Full-text search\nPostgres tsvector + GIN index\n(3× target count)"]

    Vec --> Merge["Merge + deduplicate\nby chunk ID"]
    FTS --> Merge

    Merge --> Rerank["LLM Reranker\nqwen2.5:7b cross-encoder\nreason-before-verdict\npenalises superseded docs"]

    Rerank --> Score["Parse SCORE: N\n(0–10 per chunk)"]
    Score --> TopN["Top-N chunks\nwith source metadata"]
    TopN --> Prompt["Inject as\nRETRIEVED CONTEXT\ninto generation prompt"]
```

### Reranker prompt design

The reranker receives each candidate chunk with the original question and must reason in prose before emitting a score on its final line (`SCORE: N`). Scores are parsed with `Pattern.compile("SCORE:\\s*(\\d+)")`. Unparseable output yields −1 (chunk dropped). Superseded documents (marked in their footer) are penalised by 4+ points to prevent stale rules from outranking current ones.

---

# LangGraph4j Agent Flow

```mermaid
flowchart TD
    START(["START"]) --> RETRIEVE

    RETRIEVE["RETRIEVE node\nRetrievalService.retrieveAndRerank()\nhybrid vector + FTS + LLM rerank\n⏱ marion.agent.node{node=retrieve}"]

    RETRIEVE --> TOOL_FETCH

    TOOL_FETCH["TOOL_FETCH node\nMcpToolService calls:\n• lookupTitleLien(vin)\n• lookupTaxReciprocity(originState)\n• lookupFees(transferType, county)\n⏱ marion.agent.node{node=tool-fetch}"]

    TOOL_FETCH --> GENERATE

    GENERATE["GENERATE node\nbuildUserPrompt() → RETRIEVED CONTEXT\n+ DATABASE LOOKUP RESULTS\n+ BRAND / RATE banners\n→ chatModel.chat()\n→ TransferResponseParser.parse()\n→ stores parseError in state\n⏱ marion.agent.node{node=generate}"]

    GENERATE --> Edge{{"parseError.isEmpty()\nor cycleCount ≥ 2?"}}

    Edge -->|"yes — valid or gave up"| END(["END"])
    Edge -->|"no — retry"| GENERATE

    END --> Return["TransferAgentController\nextract draftAnswer\nTransferResponseParser.parse()\n→ TransferResponse JSON"]
```

### Retry mechanics

- `cycleCount` starts at 0 and increments each GENERATE pass.
- After a failed parse the retry prompt prepends `[RETRY N] Your previous response could not be parsed. Error: <Jackson message>`.
- At `cycleCount ≥ 2` the graph exits regardless of parse state; the controller then calls `parse()` one final time, which throws `IllegalArgumentException` if the output is still malformed — caught by `GlobalExceptionHandler` → 422.
- `recursionLimit(10)` is a backstop (3 nodes × 2 cycles + retrieve + tool_fetch = 8 steps).

---

# MCP Tool Integration

```mermaid
sequenceDiagram
    participant G as TOOL_FETCH node
    participant S as McpToolService
    participant C as DefaultMcpClient
    participant M as MCP Server :8090
    participant D as PostgreSQL

    G->>S: lookupTitleLien(vin)
    S->>C: callTool("lookup_title_lien", {vin})
    C->>M: POST /mcp (Streamable HTTP)
    M->>D: SELECT * FROM title_records WHERE vin=?
    D-->>M: record (brand, lien_status, title_form, …)
    M-->>C: JSON result
    C-->>S: Optional<String>
    S-->>G: toolData["VEHICLE_RECORD"] = raw JSON

    G->>S: lookupTaxReciprocity(originState)
    S->>C: callTool("lookup_tax_reciprocity", {state})
    C->>M: POST /mcp
    M->>D: SELECT * FROM tax_reciprocity WHERE state=?
    D-->>M: {has_agreement, origin_rate_pct}
    M-->>C: JSON result
    C-->>S: Optional<String>
    S-->>G: toolData["TAX_RECIPROCITY"] = raw JSON

    G->>S: lookupFees(transferType, county)
    S->>C: callTool("lookup_fees", {type, county})
    C->>M: POST /mcp
    M->>D: SELECT * FROM fee_schedule …
    D-->>M: itemised fees
    M-->>C: JSON result
    C-->>S: Optional<String>
    S-->>G: toolData["FEE_SCHEDULE"] = raw JSON
```

### Prompt injection after tool fetch

Once tool data is assembled, `buildUserPrompt` injects two banners if the data contains them:

- **Brand banner** — if `"brand": "Rebuilt"` appears in `VEHICLE_RECORD`, injects `*** BRAND STAMP DETECTED … supervisorReferral=true REQUIRED ***` so STEP 1 fires reliably even when the model would otherwise miss a JSON field.
- **Rate banner** — if `"origin_rate_pct": 4.50` appears in `TAX_RECIPROCITY`, injects `*** ORIGIN TAX RATE (from database): 4.5% — use THIS rate exactly ***` to prevent the model from hallucinating a different rate.

---

# Data Model (PostgreSQL)

```mermaid
erDiagram
    doc_embeddings {
        uuid   id PK
        text   content
        vector embedding
        text   source
        tsvector tsv
    }

    title_records {
        varchar vin PK
        varchar origin_state
        varchar title_form
        varchar lien_status
        varchar brand
        integer odometer
        varchar insurance_expiry
    }

    tax_reciprocity {
        varchar state PK
        boolean has_agreement
        numeric origin_rate_pct
    }

    fee_schedule {
        varchar transfer_type
        varchar county
        numeric title_fee
        numeric vin_fee
        numeric registration_fee
        numeric emissions_fee
        numeric lien_release_fee
    }

    inspection_stations {
        integer id PK
        varchar county
        varchar inspection_type
        varchar station_name
    }
```

---

# Prompt Architecture

The generation prompt is built in two layers:

**System prompt (static):**
- STEP 1: Brand/lien scan with explicit trigger words, scanning caution for data fields vs brand fields, Marion equivalency instruction
- STEP 2: Tax formula (4 explicit steps with worked examples A/B/C), emissions age formula with examples, superseded-rule rejection, database-is-authoritative rule
- Output schema: exact JSON shape with field-by-field instructions for referral vs normal path

**User prompt (dynamic, per request):**
```
RETRIEVED CONTEXT:
--- [1] source.md ---
<chunk text>

DATABASE LOOKUP RESULTS (authoritative):
VEHICLE_RECORD: {"vin":…,"brand":"Rebuilt",…}
TAX_RECIPROCITY: {"has_agreement":true,"origin_rate_pct":4.50}
FEE_SCHEDULE: {…}

*** BRAND STAMP DETECTED IN VEHICLE RECORD: "Rebuilt" — supervisorReferral=true REQUIRED ***
*** ORIGIN TAX RATE (from database): 4.5% — use THIS rate exactly ***

VEHICLE VIN: 1HAL0000001000001
ORIGIN STATE: Halloway
REGISTRATION COUNTY: Marion County
TRANSFER TYPE: PURCHASE

QUESTION: <examiner question>

[RETRY 1] Your previous response could not be parsed. Error: <Jackson message>   ← only on retry
```
