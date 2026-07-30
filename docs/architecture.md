# Marion DMV — System Architecture

## System Overview

```mermaid
graph TD
    Browser["Browser\nAngular 21 + Bootstrap 5\n:4200 (dev)"]

    subgraph marion-app ["marion-app — Spring WebFlux :8080"]
        PII["PiiGuardrailFilter\n@Order(-100)\nblocks SSN / card patterns"]
        TC["TransferController\nPOST /api/transfer/query"]
        TAC["TransferAgentController\nPOST /api/transfer/query/agent\nPOST /api/transfer/query/agent/resume"]
        Graph["LangGraph4j Agent\nRETRIEVE → TOOL_FETCH → GENERATE → await_supervisor"]
        Checkpoint[("MemorySaver\nin-process checkpoint\nkeyed by threadId")]
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
    TAC -->|"invoke(inputs, threadId)"| Graph
    TAC -->|"resume(threadId, decision)"| Graph
    Graph -->|"checkpoint after GENERATE\ninterruptBefore(await_supervisor)"| Checkpoint
    Checkpoint -->|"getState().next()\n== await_supervisor?"| TAC
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
| `TransferAgentController` | Delegates to the compiled LangGraph4j graph; issues a fresh `threadId` per `/query/agent` call; returns `AgentTransferResponse` (wraps `TransferResponse` + `awaitingSupervisorDecision` + `threadId`); `/query/agent/resume` re-enters a paused checkpoint by `threadId` |
| `TransferAgentGraph` | Defines RETRIEVE → TOOL_FETCH → GENERATE → `await_supervisor` graph with a conditional retry edge and a conditional referral-pause edge; each node timed via `marion.agent.node` Micrometer metric |
| `await_supervisor` node | No-op gate node; its only purpose is a named `interruptBefore()` target. The graph halts here — after GENERATE's output is committed, before this node's body runs — whenever `supervisorReferral=true` |
| `MemorySaver` | LangGraph4j in-process `BaseCheckpointSaver`; persists graph state keyed by `threadId` so a paused run can be resumed later in the same process. **Not durable across app restarts** — a real deployment needs a Postgres-backed saver (not shipped by LangGraph4j) |
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
| `POST` | `/api/transfer/query/agent` | `TransferAgentController` | `AgentTransferResponse` (JSON, 200) — `{response, awaitingSupervisorDecision, threadId}` |
| `POST` | `/api/transfer/query/agent/resume` | `TransferAgentController` | `AgentTransferResponse` (JSON, 200) — resumes a paused `threadId` with `{decision, note}`; does not re-invoke the LLM |
| `POST` | `/api/transfer/stream` | `TransferController` | SSE phase/token/result events (non-agent path; no checkpointing, so no HITL pause) |
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

    GENERATE --> Edge{{"routeAfterGenerate(state)\n(checks supervisorDecision present?)"}}

    Edge -->|"parse failed, cycles left in phase"| GENERATE
    Edge -->|"parsed OK, first pass,\nsupervisorReferral=true"| AWAIT["await_supervisor\n(no-op gate node)\ninterruptBefore fires here"]
    Edge -->|"parsed OK, no referral —\nor already post-decision"| END(["END"])

    AWAIT -->|"graph halts here until resumed via\nGraphResume(threadId, decision, note)"| RESUME{{"resume: merge decision+note\ninto state, re-enter at await_supervisor"}}
    RESUME --> GENERATE

    END --> Return["TransferAgentController\nextract draftAnswer\nTransferResponseParser.parse()\n→ AgentTransferResponse JSON"]
```

### Retry mechanics

- `cycleCount` starts at 0 and increments each GENERATE pass, shared across both the first pass and
  the post-supervisor-decision pass (it is never reset).
- After a failed parse the retry prompt prepends `[RETRY N] Your previous response could not be parsed. Error: <Jackson message>` — independent of, and additive with, the supervisor-review block (see below), so a parse-retry mid post-review pass doesn't lose the decision context.
- `routeAfterGenerate` caps retries at `FIRST_PASS_MAX_CYCLES=2` for the first pass, or
  `FIRST_PASS_MAX_CYCLES + POST_REVIEW_MAX_CYCLES=4` total once `supervisorDecision` is present in
  state — and once present, the edge never routes back to `await_supervisor`, only to `generate` or `end`.
- `recursionLimit(14)` is a generous backstop, not a tight budget: retrieve + tool_fetch + up to 4
  generate cycles (across both phases) + await_supervisor + end.
- Retry and referral-pause are mutually exclusive per cycle: `routeAfterGenerate` only checks `supervisorReferral` once parsing has already succeeded, so a malformed response is always retried before referral status is ever consulted.

---

# Human-in-the-Loop Supervisor Review

Real pause/resume, not a display-only flag: when GENERATE produces `supervisorReferral=true`, the graph
execution itself halts before advancing past `await_supervisor`, and stays halted — potentially indefinitely —
until a supervisor's decision arrives on a separate HTTP call. On resume, the decision and note are **fed
back to the model** — `await_supervisor` routes to a second GENERATE pass (not straight to END), so the
agent produces a genuinely finalized response rather than just unblocking the original draft.

```mermaid
sequenceDiagram
    participant U as Examiner (UI)
    participant TAC as TransferAgentController
    participant G as CompiledGraph
    participant CP as MemorySaver (checkpoint)
    participant Sup as Supervisor (UI)
    participant LLM as chatModel

    U->>TAC: POST /query/agent {question, ...}
    TAC->>TAC: threadId = UUID.randomUUID()
    TAC->>G: invoke(inputs, config[threadId])
    G->>G: RETRIEVE → TOOL_FETCH
    G->>LLM: GENERATE (pass 1) — finds the exception
    G->>CP: checkpoint(state, nextNodeId=await_supervisor)
    G-->>TAC: paused — interruptBefore(await_supervisor)
    TAC->>CP: getState(config).next()
    CP-->>TAC: "await_supervisor"
    TAC-->>U: AgentTransferResponse{response, awaitingSupervisorDecision=true, threadId}

    Note over U,Sup: Referral banner + "Awaiting Supervisor Decision" card render.<br/>Run stays paused — no timeout, no polling required.

    Sup->>TAC: POST /query/agent/resume {threadId, decision, note}
    TAC->>G: invoke(GraphResume({supervisorDecision, supervisorNote}), config[threadId])
    G->>CP: load checkpoint by threadId, merge resume data into state
    G->>G: await_supervisor (no-op) routes to GENERATE again
    G->>LLM: GENERATE (pass 2) — prior draft + decision + note fed back in
    Note over LLM: APPROVED → finalize STEP 2 (checklist, fees, taxOwed) as if never blocked.<br/>DENIED → keep blocked, fold the note into conditionalNote.
    G-->>TAC: final state (draftAnswer = pass-2 output) → END
    TAC->>CP: getState(config).next()
    CP-->>TAC: null (reached END)
    TAC-->>Sup: AgentTransferResponse{response, awaitingSupervisorDecision=false, threadId}
```

### Design notes

- **Detecting a pause is checkpoint-driven, not stream-driven.** `graph.invoke()` returns the graph's state
  either way; the only reliable signal for "did this pause or finish?" is `graph.getState(config).next()`,
  which reads the persisted `Checkpoint.nextNodeId` — it equals `"await_supervisor"` when paused, `null`
  when the run reached `END`.
- **Resume triggers exactly one more GENERATE pass, never a re-pause.** `routeAfterGenerate` checks
  `state.supervisorDecision().isPresent()`: once a decision has been merged into state (which only happens
  via resume), the graph will never route back to `await_supervisor` again, no matter what the second
  pass's own `supervisorReferral` value comes out as. Retry-on-parse-failure still applies within each
  phase (`FIRST_PASS_MAX_CYCLES` + `POST_REVIEW_MAX_CYCLES`, both = 2 — `recursionLimit(14)` is a generous
  backstop, not a tight budget).
- **The second pass gets the first pass's full draft, not just the decision.** `buildSupervisorReviewBlock()`
  includes the prior `draftAnswer` verbatim plus the decision/note, and instructs the model explicitly:
  APPROVED → run STEP 2 to completion (fold `conditionalChecklist` into `checklist`, compute fees/tax,
  clear `supervisorReferral`); DENIED → stay blocked, explain the denial in `conditionalNote`. This block is
  appended *after* the original STEP 1 trigger banners in the prompt and explicitly says it supersedes them
  — otherwise a weaker model can re-trigger STEP 1 from the earlier banner instead of honoring the decision.
- **`threadId` is the resume key.** It's generated per `/query/agent` call, returned to the client in
  `AgentTransferResponse`, and must be echoed back verbatim on `/query/agent/resume`. There is no
  server-side list of pending referrals in this prototype — the client (UI history entry) is the only
  place `threadId` is retained.
- **`MemorySaver` is in-process memory, not a durable store.** A paused referral is lost if `marion-app`
  restarts before a supervisor decides. Acceptable for a prototype; a production deployment needs a
  Postgres-backed `BaseCheckpointSaver` (LangGraph4j ships the interface, not an implementation).
- **The non-agent path (`/api/transfer/stream`, `TransferController`) has no checkpointer and cannot pause.**
  HITL only exists on the LangGraph4j-backed `/query/agent` path — this is why the UI's `submit()` uses that
  endpoint rather than the SSE streaming one.

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
