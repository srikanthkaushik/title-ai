# Marion DMV — Running the Application

All commands below are **Windows Command Prompt** syntax (per project convention —
not PowerShell, not bash).

## Prerequisites

| Tool | Needed for |
|---|---|
| Java 21, Maven 3.9+ | `marion-app`, `marion-mcp-server` |
| Node 20+ / npm | `marion-ui` |
| Docker Desktop (or a native Postgres 16 with the `vector` extension) | pgvector store |
| Ollama, native Windows install | local LLM + embeddings runtime |
| `ANTHROPIC_API_KEY` (optional) | only if `llm.provider=anthropic`, or to run the eval suite |

## 1. Start Postgres + pgvector

The app expects Postgres on **port 5433** (not the 5432 default — see
`marion-app/src/main/resources/application.properties`), database `mariondmv`,
user/password `marion`/`marion`.

```
docker run -d --name marion-pg -p 5433:5432 ^
  -e POSTGRES_USER=marion -e POSTGRES_PASSWORD=marion -e POSTGRES_DB=mariondmv ^
  pgvector/pgvector:pg16
```

Data persists in the container across `docker stop` / `docker start marion-pg`;
only re-run the `docker run` line if the container was removed.

## 2. Seed the systems-of-record tables

Loads `vehicles`, `tax_reciprocity`, `fee_schedule`, `marion_counties`,
`inspection_stations` — the tables the MCP tools query. Idempotent (drops and
re-inserts), safe to re-run any time.

```
psql -h localhost -p 5433 -U marion -d mariondmv -f test-data\sql\seed.sql
```

The `doc_embeddings` table used for RAG is created separately by the app itself
(`PgVectorEmbeddingStore.createTable(true)` in `RagConfig`) — nothing to do
here for that one.

## 3. Pull the Ollama models

```
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

Confirm the Ollama server is listening on `http://localhost:11434` (it starts
automatically on Windows once installed).

## 4. Build

From the repo root:

```
mvn install
```

This builds both `marion-app` and `marion-mcp-server` (declared as modules of
the `marion-dmv-parent` root pom).

## 5. Run the MCP tool server (port 8090)

```
mvn -pl marion-mcp-server spring-boot:run
```

Serves `lookup_title_lien`, `decode_vin`, `lookup_tax_reciprocity`,
`lookup_fees`, `check_county_emissions`, `check_inspection_stations` over
Streamable HTTP MCP at `http://localhost:8090/mcp`. `marion-app` degrades
gracefully (empty tool set) if this isn't running, but referral/fee/tax
answers will be missing tool-sourced data.

## 6. Run the main app (port 8080)

```
mvn -pl marion-app spring-boot:run
```

Defaults to `llm.provider=ollama`. To use Anthropic instead:

```
set ANTHROPIC_API_KEY=sk-ant-...
mvn -pl marion-app spring-boot:run -Dspring-boot.run.arguments=--llm.provider=anthropic
```

`corpus.base-path` in `application.properties` is currently a hardcoded
absolute path (`C:/DEVL/TITLE/test-data/corpus`) — update it if the repo is
ever cloned somewhere else.

## 7. Ingest the document corpus

One-time (or after corpus edits) — wipes and reseeds the vector store from
`test-data/corpus/`:

```
curl -X POST "http://localhost:8080/api/ingest/reset?confirm=true"
```

## 8. Run the Angular UI (dev, port 4200)

```
cd marion-ui
npm install
npm start
```

`npm start` runs `ng serve`; `angular.json` already wires
`proxy.conf.json` (`/api` → `http://localhost:8080`) into the serve
config, so no extra flag is needed. Open `http://localhost:4200`.

## 9. Production build (single jar, UI bundled)

```
mvn -pl marion-app -am install -Pwith-ui
```

The `with-ui` profile runs `npm run build` in `marion-ui` and copies
`marion-ui/dist/marion-ui/browser` into `marion-app`'s
`static/` resources, so the resulting jar serves the UI itself — no separate
`ng serve` needed in this mode.

## Quick smoke test (no UI)

```
curl -X POST http://localhost:8080/api/transfer/query -H "Content-Type: application/json" -d "{\"question\":\"What is required to title a vehicle relocating from Verdana?\",\"vehicleVin\":null,\"originState\":\"Verdana\",\"county\":\"Marion\",\"transferType\":\"RELOCATION\"}"
```

(`-d "{\"...\"}"` with backslash-escaped quotes is the correct form for
`curl.exe` under `cmd.exe` — do not use PowerShell-style quoting here.)

## Running the eval suite

Both `TransferEvalTest` and `RetrievalEvalTest` are pinned to Anthropic via
`@ActiveProfiles("eval")` (`application-eval.properties`), independent of
whatever `llm.provider` the running app uses:

```
set ANTHROPIC_API_KEY=sk-ant-...
mvn -pl marion-app test -Dtest=TransferEvalTest,RetrievalEvalTest
```

Postgres and the MCP server (step 1, 2, 5 above) must already be running —
the evals hit the real retrieval pipeline and real tool data, not mocks.

## Troubleshooting

See the gotchas table in `PROJECT.md` (root of repo) for known failure modes —
MCP/Spring AI version mismatches, qwen2.5:7b quirks, WebFlux streaming traps,
etc. — before re-deriving a fix for something already hit once.
