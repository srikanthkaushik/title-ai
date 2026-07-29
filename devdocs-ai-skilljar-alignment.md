# DevDocs AI ↔ "Building with the Claude API" — Alignment & Study Plan

*A companion to the DevDocs AI Java tutorial. Use this to follow Anthropic's Skilljar
course "Building with the Claude API" while building your labs in Java instead of Python.*

---

## 1. The Skilljar course at a glance

"Building with the Claude API" is a seven-module video course (~8 hours). The modules:

1. **API fundamentals** — setup, authentication, API key management, single- and
   multi-turn conversations, message formatting, context handling, system prompts,
   temperature, response streaming, structured output
2. **Prompt engineering** — advanced prompting techniques
3. **Tool use** — tool integration and design
4. **Retrieval systems** — RAG, embeddings, vector search, citations
5. **Agentic workflows** — parallelization, chaining, routing, orchestration
6. **Multimodal** — processing text, images, and documents
7. **Wrapping up / Anthropic Apps** — Claude Code, Computer Use, agent-based systems

The course teaches the **raw Anthropic API** via the Python SDK. Project types built
include chat interfaces, evaluation pipelines, tool-enabled systems, RAG
implementations, and autonomous agents.

---

## 2. Module → DevDocs phase alignment

| Skilljar module          | DevDocs AI phase                                              | Status |
|--------------------------|--------------------------------------------------------------|--------|
| 1. API fundamentals      | Phase 1 + Phase 1.1 — streaming chat, system prompt, multi-turn, structured output | ✅ Complete |
| 2. Prompt engineering    | Phase 1.2 — role, XML, multishot, CoT, prefill, chaining      | ✅ Complete |
| 3. Tool use              | Phase 3.0 (raw loop) + Phase 3 (MCP tools: GitHub, Maven, JShell) | ✅ Complete |
| 4. Retrieval systems     | Phase 2 — pgvector RAG, hybrid search, citations             | ✅ Complete |
| 5. Agentic workflows     | Phase 4 — LangGraph4j planner/retriever/coder/critic, routing| ✅ Complete |
| 6. Multimodal            | Phase 1.1 extensions — image + PDF input (raw SDK)           | ✅ Complete |
| 7. Anthropic Apps / evals| Phase 5 — evals, guardrails, observability, reranker          | ✅ Substantially complete (semantic cache + CI deferred) |

**All seven modules covered.** Phase 5 has two deliberately deferred items (semantic cache,
CI gate) — see §14 for the reasoning behind each.

**Ordering note:** the course does tool use (Module 3) *before* retrieval (Module 4);
DevDocs does RAG (Phase 2) *before* tools (Phase 3). Follow the mapped phase for each
module rather than expecting lockstep order.

---

## 3. The caveat that matters most: LangChain4j vs the raw API

The Skilljar course teaches the **raw Anthropic API** — message arrays, content blocks,
`tool_use` / `tool_result` block structure, streaming SSE events, the `/v1/messages`
endpoint shape. DevDocs AI uses **LangChain4j**, which deliberately abstracts all of
that away behind `model.chat(...)` and `AiServices`.

- If your goal is **"build a real Java AI app"** → LangChain4j is great, keep going.
- If your goal is **"understand the Claude API mechanics the course teaches"** (e.g.
  for the Claude Certified Architect exam) → you need to see the raw API at least once.

**Recommendation:** build at least one lab directly against the official
**`anthropic-java`** SDK, which mirrors the Python SDK closely so the course's examples
translate almost line-for-line. That is what **Phase 1.1** (§6) is for.

---

## 4. Gaps to fill separately

Things the course covers that DevDocs AI does not — and their current status:

- **Multi-turn conversation / context management** — ✅ filled by Phase 1.1 (manual
  history replay with `addUserMessage` / `addAssistantMessage`). A `ChatMemory`-based
  LangChain4j version is still a nice optional add for the main app.
- **Multimodal (Module 6)** — ✅ filled by Phase 1.1 (image content blocks + base64 PDF
  document blocks via the beta namespace).
- **Raw message / content-block format** — ✅ filled by Phase 1.1.
- **Prompt engineering (Module 2)** — ✅ filled by Phase 1.2 (role, XML tags, multishot,
  chain of thought, prefill, prompt chaining).
- **Long-context tips (Module 2)** — ✅ filled by Phase 2 (retrieved context is placed
  above the question, each chunk delimited with its source, Claude instructed to ground
  answers in the provided context).

---

## 5. Recommended study plan

1. Watch each course module, then build the mapped DevDocs phase:
   Module 1 → Phase 1, Module 3 → Phase 3, Module 4 → Phase 2, Module 5 → Phase 4.
2. Do **Phase 1.1** (raw `anthropic-java`) right after Phase 1 so you see the actual API
   the course is built on — message arrays, system prompts, multi-turn, streaming events.
3. Consider a second raw-SDK lab at **tool use** (Phase 3), where the abstraction hides
   the most (the `tool_use` / `tool_result` round-trip).
4. Add two mini-labs DevDocs skips:
   - **Multi-turn chat memory** (after Phase 1) — partially covered by Phase 1.1.
   - **Multimodal** (image or PDF input) to cover Module 6.

---

## 6. Phase 1.1 — Raw `anthropic-java` lab  ✅ COMPLETE

**Goal:** rebuild Phase 1's chat against the official Anthropic Java SDK instead of
LangChain4j, so you see the raw message format, system prompts, multi-turn context, and
streaming events the Skilljar course teaches. Also fills the multi-turn and multimodal
gaps from §4.

**Dependency** (check Maven Central for the latest version):

```xml
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java</artifactId>
  <version>2.11.1</version>
</dependency>
```

**What was built:** a standalone console class `RawApiLab` (no Spring, no WebFlux) with a
`main` method, run from the IDE, exercising seven things:

| Step | Concept | Skilljar module |
|------|---------|-----------------|
| 1 | Basic `create` — content blocks, `usage`, `stopReason` | 1 |
| 2 | System prompt via `.system(...)` (top-level param, not a message) | 1 |
| 3 | Multi-turn context — manual `addUserMessage` / `addAssistantMessage` replay | 1 |
| 4 | Streaming — `StreamResponse<RawMessageStreamEvent>` → text deltas | 1 |
| 5 | `MessageAccumulator` — rebuild full `Message` (with usage) from a stream | 1 |
| 6 | Image input — `ImageBlockParam` + `Base64ImageSource` content blocks | 6 |
| 7 | PDF input — `BetaRequestDocumentBlock` + base64 source, beta namespace + header | 6 |

**Key SDK shapes:**

```java
AnthropicClient client = AnthropicOkHttpClient.fromEnv(); // reads ANTHROPIC_API_KEY

MessageCreateParams params = MessageCreateParams.builder()
    .model("claude-sonnet-4-5-20250929")
    .maxTokens(1024L)
    .system("You are DevDocs AI, an expert developer assistant.")
    .addUserMessage("What is connection pooling in Spring Boot?")
    .build();

Message message = client.messages().create(params);

// Streaming + accumulation
MessageAccumulator accumulator = MessageAccumulator.create();
try (StreamResponse<RawMessageStreamEvent> stream =
         client.messages().createStreaming(params)) {
    stream.stream()
        .peek(accumulator::accumulate)
        .flatMap(event -> event.contentBlockDelta().stream())
        .flatMap(delta -> delta.delta().text().stream())
        .forEach(textDelta -> System.out.print(textDelta.text()));
}
Message full = accumulator.message();  // usage + stopReason available here
```

**Gotchas hit and resolved:**
- Image/PDF source `mediaType` on the PDF block is a `JsonValue`, not a `String` — wrap
  with `JsonValue.from("application/pdf")` (or omit; it defaults).
- `addBeta(...)` only exists on the **beta** `MessageCreateParams` — fully-qualify
  `com.anthropic.models.beta.messages.MessageCreateParams.builder()` since the file
  already imports the non-beta one.
- "PDF specified was not valid" (400) = the bytes aren't a real/unencrypted PDF; verify
  the file header is `%PDF`.

**Big takeaway:** *everything is content blocks* — text, images, PDFs, and (Phase 3)
tool calls / tool results are all typed blocks in a list. LangChain4j hides this:
`onPartialResponse()` ≈ iterating `contentBlockDelta` text deltas; `SystemMessage.from()`
≈ `.system(...)`; its message list ≈ the raw `addUserMessage` / `addAssistantMessage`
chain.

---

## 7. Phase 1.2 — Prompt engineering (Module 2)  ✅ COMPLETE

DevDocs started with a single basic system prompt. Phase 1.2 retrofits Anthropic's
canonical techniques — applied to the real `ChatController` system prompt (LangChain4j
main app), dropping into the raw lab only for prefill.

| Step | Technique | Where applied | What it does |
|------|-----------|---------------|--------------|
| 1 | Clear & direct + role + XML tags | `ChatController` system prompt | `<role>` / `<instructions>` / `<output_format>` structure; "ONLY JSON, no preamble" |
| 2 | Multishot examples | `ChatController` system prompt | `<examples>` with ideal Q&A pairs locking in format & tone |
| 3 | Chain of thought | `ChatController` + `DevDocsResponse` | `reasoning` as the **first** JSON field — CoT inside valid JSON |
| 4 | Prefill | `RawApiLab` | Prefill assistant turn with `{` to guarantee JSON (prepend `{` since prefill isn't echoed) |
| 5 | Prompt chaining | `RawApiLab` | Draft → review chain; previews Phase 4 Coder→Critic |

**Key techniques & gotchas learned:**
- Anthropic technique order (broad → specialized): be clear & direct, multishot examples,
  chain of thought, XML tags, role prompting, prefill, prompt chaining, long-context tips.
- CoT + structured output: put the reasoning field **first**; field order is the whole
  trick (tokens generate left-to-right). Strip `reasoning` before showing end users.
- Prefill is **not** echoed back in the response, and must not end in whitespace.
- Chaining is just app code passing one call's output into the next; the model stays
  stateless. LangGraph4j (Phase 4) automates this as a typed state graph.
- Not yet applied from the module: long-context tips (relevant once Phase 2 RAG injects
  large retrieved context).

---

## 8. Phase 2 — RAG with pgvector (Module 4)  ✅ COMPLETE

Grounds answers in real documentation with citations. **Adapted from the tutorial**, which
assumes OpenAI embeddings — this build uses **local Ollama** instead (free, no API key).

**Stack decisions (differ from the tutorial):**
- Embeddings: **Ollama `nomic-embed-text`, native Windows install** (not OpenAI, not Docker)
- Vector dims: **768** (not OpenAI's 1536) — but never hardcoded; see below
- pgvector: **Docker** (`pgvector/pgvector:pg16`). Native Windows Postgres was considered
  and rejected — pgvector ships no official Windows binaries and must be compiled with
  Visual Studio C++ / `nmake`. Docker sidesteps this entirely.
- Dependencies: **`langchain4j-bom` 1.13.0** imported in `<dependencyManagement>`, so all
  LangChain4j modules (anthropic, ollama, pgvector, core, tika) are version-aligned with
  no explicit `<version>` tags. This solved the recurring version-mismatch pain.

**What was built:**

| Step | Component | Notes |
|------|-----------|-------|
| 1 | Infrastructure | Native Ollama + `devdocs-pg` container |
| 2 | Dependencies via BOM | `langchain4j`, `-ollama`, `-pgvector`, `-document-parser-apache-tika` |
| 3 | `RagConfig` | `OllamaEmbeddingModel` + `PgVectorEmbeddingStore` beans |
| 4 | `IngestionService` | `EmbeddingStoreIngestor`, recursive splitter (512/64), source metadata |
| 5 | `RetrievalService` | Embed query → vector search → build context block + sources |
| 6 | Real ingestion | `FileSystemDocumentLoader` / `UrlDocumentLoader` + `ApacheTikaDocumentParser` |
| 7 | Hybrid search | `KeywordSearchService` (Postgres FTS via `JdbcTemplate`) merged with vector hits |

**Key technique — dimension mismatch, solved structurally:**
```java
PgVectorEmbeddingStore.builder()
    .dimension(embeddingModel.dimension())  // derives 768 from Ollama at boot
    .build();
```
The store's column width can never drift from the model. (`OllamaEmbeddingModel` does one
test embedding at startup to discover the size — so Ollama must be running when the app boots.)

**Hybrid search — why it matters:** pure vector search matches on *meaning* and fumbles
exact literals (error codes, API names, config keys like
`spring.datasource.hikari.maximum-pool-size`). Postgres full-text search nails those.
Merge both, dedup by chunk text:
```sql
ALTER TABLE doc_embeddings ADD COLUMN fts tsvector
  GENERATED ALWAYS AS (to_tsvector('english', text)) STORED;
CREATE INDEX idx_docs_fts ON doc_embeddings USING GIN (fts);
```
(Column is `text`, and source lives in `metadata->>'source'` — LangChain4j's schema.)

**Gotchas hit and resolved:**
- **Silent RAG bypass.** `ChatController` was missing its `RetrievalService` wiring, so
  retrieval never ran and Claude answered unaugmented — no error, just vague answers.
  Debug technique: `System.out.println` the retrieved context. If even the *static* text
  doesn't print, the method isn't being called at all.
- **Markdown code fences break JSON parsing.** Claude wrapped output in ` ```json `.
  Fix: instruct explicitly ("no code fences; start with `{`, end with `}`") and/or strip
  fences defensively before `ObjectMapper`. This is exactly what raw-API **prefill**
  (Phase 1.2) prevents structurally — a concrete cost of the LangChain4j abstraction.
- **`Connection to localhost:5432 refused`** = the pgvector container stopped (e.g. after
  a machine restart). `docker start devdocs-pg` — **data persists** across stop/start.
- **Apache Tika's role:** loaders fetch raw *bytes*; Tika converts them to clean text
  (strips HTML tags, decodes PDF/docx) before chunking. Inline strings in Step 4 needed no
  parser; real files do. It's the general-purpose replacement for the tutorial's
  `HtmlTextSegmenter`.
- **Ollama embeds on CPU** — large documents split into hundreds of chunks and are slow.
  The tradeoff for free local embeddings: no rate limits, but bounded by your CPU.

**Known shortcut (revisit in Phase 5):** query embedding is a blocking call made on the
WebFlux event-loop thread. Fine for a lab; production should offload to
`Schedulers.boundedElastic()`.

---

## 9. Phase 3.0 — Raw tool use lab (Module 3, part 1)  ✅ COMPLETE

A hand-written agentic tool loop in `RawApiLab`, before letting LangChain4j/MCP abstract
it away. Recommended in §5 step 3 — this is where the abstraction hides the most.

**The loop (the heart of Module 3):**
1. Send message **+ tool schemas** (name, description, JSON input schema).
2. Claude stops with `stop_reason: "tool_use"` and a **`tool_use` content block**
   (tool name, correlation `id`, and the arguments it chose).
3. **Your code** executes the tool. *Claude never executes anything.*
4. Replay Claude's `tool_use` turn, then send a **`tool_result` block** with the matching
   `toolUseId` and your output.
5. Claude reads the result and answers.

`tool_use` / `tool_result` are just more **content block types** — same structure as text,
images, and PDFs from Phase 1.1.

**Tool built:** `lookup_maven_version` — queries Maven Central for the current version of
an artifact. Genuinely useful, since Claude's training data goes stale on library versions.

**Key API shapes:**
```java
Tool.builder()
    .name("lookup_maven_version")
    .description("...")              // <-- the ONLY thing Claude uses to decide to call it
    .inputSchema(...)
    .build();

// after the first response:
if (block.toolUse().isPresent()) {
    ToolUseBlock toolUse = block.toolUse().get();
    String result = executeMavenLookup(...);          // you run it

    convo.addAssistantMessageOfBlockParams(
            List.of(ContentBlockParam.ofToolUse(toolUse.toParam())));   // replay Claude's turn
    convo.addUserMessageOfBlockParams(List.of(
            ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                            .toolUseId(toolUse.id())   // MUST match
                            .content(result)
                            .build())));
    Message finalResponse = client.messages().create(convo.build());
}
```

**Gotchas hit and resolved:**
- `toolUse._input().asObject()` returns an untyped `Object` — don't try to assign it to
  `Map<String, JsonValue>`. Use `new ObjectMapper().valueToTree(toolUse._input())` →
  `JsonNode`. (Note: **Spring Boot 4 ships Jackson 3** — package is `tools.jackson.*`,
  not `com.fasterxml.jackson.*`.)
- `_input().toString()` yields Java `Map.toString()` (`{k=v}`), **not** JSON — feeding it
  to Jackson's `readTree` throws. Use `valueToTree` instead of a string round-trip.
- Tool descriptions are the trigger. Vague descriptions = tool never fires.

**The big lesson — tool quality IS answer quality:**
The loop worked on the first try; the *tool* was wrong for an hour. `search.maven.org`'s
Solr index is **stale** — it confidently returned `1.0.0` for `langchain4j-core` when the
real latest was `1.17.2`. No query tuning (`core=gav`, `sort=timestamp desc`) fixed it,
because the index itself was bad. Claude faithfully relayed the garbage; it *cannot* know
the tool lied.

Fix = better data source. `maven-metadata.xml` is generated from the repository itself and
is authoritative:
```java
String path = groupId.replace('.', '/');
String url = "https://repo1.maven.org/maven2/%s/%s/maven-metadata.xml"
        .formatted(path, artifactId);
// return the raw XML — Claude parses it fine. Tools may return semi-structured data.
```

**Takeaways:**
- Tool debugging *is* API debugging. Most agentic engineering time goes into tool
  correctness and reliability, not the model loop.
- Source selection is a design decision: "search Maven Central" and "read Maven Central's
  metadata" sound equivalent but differ wildly in reliability.
- Tools can return raw XML/JSON — the model is a capable parser.

---

## 10. Dual LLM provider setup (Anthropic ↔ Ollama)  ✅ COMPLETE

**Motivation:** cost control + the ability to run strictly offline. Rather than switching
wholesale, the provider is configurable — flip one property.

**Important tension to keep in view:** DevDocs-*as-a-product* can run local. DevDocs-*as-a-
Skilljar-lab* cannot — the course is specifically about the **Claude API**, and `RawApiLab`
is bound to the Anthropic SDK by design. Recommended workflow: **develop/iterate on Ollama**
(free, offline, no key), **follow the course on Anthropic**.

Also note "offline" is partial by nature here: `DevDocsTools` calls Maven Central over HTTP,
so a strictly network-isolated DevDocs can't do live version lookups at all. "Offline" means
*the model runs locally*, not *the system has no network*.

**Config:**
```properties
llm.provider=anthropic          # or: ollama
llm.ollama.base-url=http://localhost:11434
llm.ollama.chat-model=qwen2.5:14b
```

**Implementation:** `LlmConfig` branches on `llm.provider` for both `StreamingChatModel` and
`ChatModel`. Everything downstream (`ChatController`, `RetrievalService`, `DevDocsTools`,
`AiServices`) is untouched — this is exactly the abstraction LangChain4j exists to provide.

**Two details that matter:**
- `@Value("${anthropic.api-key:}")` — the trailing colon gives an empty default, so the app
  boots in Ollama mode **with no API key set at all**. Without it, Spring fails on the
  missing property and true offline operation is impossible.
- Log the active provider at startup (`>>> LLM provider: OLLAMA (...)`) — otherwise you will
  confuse yourself about which model produced an answer.
- Set `.temperature(0.0)` on the Ollama chat model — helps weaker models pick tools reliably.

**Model choice:** tool calling is where local models are typically weakest — needs a
tool-capable model. **In use here: `qwen2.5:7b`, built with an 8K token context limit.**

> Note: conventional advice says you need 14B+ for reliable tool use. That proved
> **wrong** here — the 7B model handled single tools, same-tool chaining, and cross-tool
> composition correctly (see eval below). Don't assume small models can't do this.

**The 8K context limit is the real constraint to watch:**
- Tool loops accumulate context fast — each round-trip replays the full conversation plus
  tool outputs. Aggressive tool-output trimming (see `summarize()` in Phase 3) is doing
  double duty as context management.
- **RAG + tools together is the danger zone**: 5 retrieved chunks + tool schemas + tool
  results can approach 8K quickly. If Ollama answers start degrading or truncating,
  suspect context exhaustion first.
- **Phase 4 will feel this hardest** — multi-agent state passing through
  planner → retriever → coder → critic balloons context.

Set `.temperature(0.0)` on the Ollama chat model — helps with reliable tool selection.

### Eval: Claude Sonnet 4.5 vs qwen2.5:7b on tool use

Same questions, both providers — a hand-run version of what Phase 5 formalizes as a JUnit +
LLM-as-judge suite.

| Scenario | Claude Sonnet 4.5 | qwen2.5:7b |
|---|---|---|
| Single tool, clean input (`log4j-core` version) | ✅ 2.26.1 | ✅ 2.26.1 (identical) |
| Single tool (GitHub rate-limiter search) | ✅ | ✅ (identical results) |
| Same-tool chaining (`caffeine`: search → refine) | ✅ | ✅ |
| **Cross-tool composition** (GitHub search → Maven lookup) | ✅ | ✅ |
| JShell: compute 20th Fibonacci | ✅ 6765 (iterative) | ✅ 6765 (recursive) |
| JShell: infinite-loop timeout | ✅ reported, stopped | ✅ recovered — rewrote & re-ran (see §11) |
| **Repairing malformed input** ("artifact called org.apache.logging.log4j" — a groupId, not an artifact) | ✅ inferred the user meant `log4j-core` | ❌ passed empty `artifactId`, then mangled the coordinates |

**The cross-tool result is the interesting one.** Both planned the sequence unprompted
(GitHub first → extract artifact → Maven lookup). They differed in *how*:
- Claude supplied `groupId='com.github.ben-manes.caffeine'` from its own knowledge.
- qwen passed `groupId=''` — **following the tool contract literally** ("if not certain, pass
  an empty string, do NOT guess"). Arguably the *more correct* behavior; the fallback path
  handled it.

**Principle: a well-specified tool contract lets a weaker model reach the same answer by a
different route.** Good tool design narrows the capability gap.

**Verdict:** the gap is **not tool use — it's error recovery.** qwen executes a
well-specified contract reliably; what it lacks is the judgment to repair a badly-posed
request. Design implication: **the weaker the model, the more your tools must refuse bad
input rather than attempt it** (hence the "ERROR: artifactId is required…" guard, which
*teaches the model what to do next* rather than returning a 404).

Cost/offline goals are **achievable for Phases 1–3**. Phase 4's planner/critic loops (plus
the 8K context limit) remain the open question.

---

## 11. Phase 3 — MCP & tool use (Module 3, part 2)  🔄 IN PROGRESS

**Step 1 — `@Tool` + `AiServices`** ✅

The entire hand-written Phase 3.0 loop collapses into an annotation. LangChain4j derives the
JSON schema from the **method signature**, detects `stop_reason: tool_use`, dispatches to your
bean, matches `toolUseId`, and replays the conversation — *iteratively*, which is why
multi-call chains work with no orchestration code.

```java
@Component
public class DevDocsTools {
    @Tool("Look up the latest released version of a Maven artifact on Maven Central. "
        + "If you are not certain of the groupId, pass an empty string — the tool will "
        + "search and return candidates. Do NOT guess a groupId.")
    public String lookupMavenVersion(
            @P("Maven groupId. Pass an empty string if you are not certain.") String groupId,
            @P("Maven artifactId, e.g. langchain4j-core") String artifactId) { ... }
}

@Bean ToolAssistant toolAssistant(ChatModel chatModel, DevDocsTools tools) {
    return AiServices.builder(ToolAssistant.class)
            .chatModel(chatModel)
            .tools(tools)          // scans @Tool methods
            .build();
}
```
Uses a **non-streaming** `ChatModel` — the tool loop needs complete responses to detect
`tool_use`. The existing streaming `/api/chat` endpoint is untouched.

**Tool design is engineering, not decoration.** The first version required a `groupId` the
user never supplied — so the model *inferred it from training data*, an unverified guess
embedded in a tool call. Silent failure mode: wrong groupId → 404 → "not found" reported for
an artifact that exists. Hardened by:
1. Letting the model pass an **empty groupId** to signal uncertainty (and telling it to, in
   the description — the description is the model's only instruction manual).
2. **Falling back to search** if a supplied groupId 404s, rather than propagating the error.
3. **Warning in the tool's own output** that search-index versions are stale and to call
   again for authoritative data.

The result: Claude chained two calls on its own. *The tool's contract did the orchestration.*
This is where tool use becomes agentic behavior.

**Step 2a — GitHub search tool** ✅

Token-optional: GitHub's search API works unauthenticated (10 req/min); a `GITHUB_TOKEN`
env var raises it to 30/min. Both models used it correctly.

**Key design lesson — filtering tool output is part of tool design.** GitHub's search
response is enormous (dozens of fields per repo, nested owner/license objects, ~100 URLs).
Dumping it raw would burn thousands of tokens and bury the signal — and with an 8K local
context, would break the model outright. A `summarize()` step reduces each repo to
name / stars / language / description / URL. **You are deciding what the model is allowed
to see.** Most people skip this; it's why both models produced such clean answers.

The "teaching error message" pattern recurs: empty query → an error that tells the model
what to do next; HTTP 403 → "rate limit exceeded, try again in a minute or set
GITHUB_TOKEN." Never return a bare failure the model can't act on.

**Step 2b — JShell execution tool** ✅

Flips the direction: the model **writes Java, the app executes it, the model sees real
output**. Verified working — "compute the 20th Fibonacci number" returned `6765` as a
genuinely computed value, not a recalled one.

**Three things doing real work in the implementation:**
- **`analyzeCompletion` loop.** `JShell.eval()` requires *exactly one* complete snippet.
  Models write multi-line code. Without splitting via
  `shell.sourceCodeAnalysis().analyzeCompletion(remaining)`, only the first statement runs
  and the rest is **silently ignored** — a very easy bug to miss.
- **Custom `out` PrintStream** on `JShell.builder()`. By default a snippet's
  `System.out.println` goes to *your* console, not back to the model. Capturing it is what
  makes the tool useful at all.
- **10s timeout on a daemon thread** (`FutureTask` + `task.get(10, SECONDS)`). `while(true){}`
  would otherwise hang the request forever.

**Free isolation:** JShell's execution environment is **out-of-process by default** — a
snippet calling `System.exit(0)` kills the JShell child process, not the Spring Boot app.

**Compile errors are returned, not swallowed** — `shell.diagnostics(...)` goes back as the
tool result, so the model sees the compiler message and can self-correct. Self-correction
for free.

### Behavioral difference: Claude vs qwen on the infinite-loop test

Given `while(true) { int x = 1; }`, both hit the 10s timeout. Then they diverged:

| | Behavior |
|---|---|
| **Claude** | Reported the timeout honestly, explained the cause, *suggested* fixes. **Stopped.** |
| **qwen2.5:7b** | Read the timeout message, **rewrote the code to terminate, and re-ran it** — two `runJShell` calls, autonomous recovery. |

Neither is a bug — it's a difference in how aggressively each interprets its mandate.
Claude's is arguably *more correct*: the user said "run this," so running it, reporting
failure, and not silently substituting different code respects the instruction. qwen's is
more agentic but **took a liberty** — it changed the user's code and executed something
never requested. In a system with side effects, "helpfully" rewriting a failed operation is
how agents do surprising things. If the behavior matters, pin it down in the system prompt.

Note qwen's retry is a spontaneous mini-version of Phase 4's **critic** loop
(attempt → evaluate → revise → retry) — a mildly encouraging signal, though a self-directed
retry is far easier than a structured multi-agent state machine.

**Also:** the retry replays the full conversation + two tool results — exactly the kind of
context growth the 8K limit makes dangerous.

### Tool design principles (learned the hard way, three times over)

1. **Tool quality IS answer quality.** The model faithfully relays your bugs (stale Maven index).
2. **The signature invites invention.** Require an argument the user didn't supply, and the
   model will make one up (the groupId).
3. **Filter tool output.** You decide what the model is allowed to see (GitHub's huge JSON).
4. **Errors should teach.** A message explaining *what to do next* enables self-correction
   (the timeout message, the "artifactId is required" guard).

**Step 3 — Standalone MCP server + client** ✅

Tools extracted into a **separate Spring Boot app** (`devdocs-mcp-server`, port 8090, sibling
dir to `devdocs-ai`), exposed over MCP, consumed by the main app as a client. The payoff:
a tool call now **crosses a process boundary** — question in on 8080, `>>> MCP TOOL` fires in
the *server's* console on 8090, answer composed back in the main app. The agent has no idea
the tool lives in another application.

**Server side (Spring AI 2.0):**
- Adds a *second* AI framework (Spring AI) alongside LangChain4j — but only in the server
  module; they communicate only over the MCP wire.
- Tutorial's `io.modelcontextprotocol:mcp-spring-boot-starter:0.9.0` is **obsolete**.
  Current: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` via
  `spring-ai-bom` (needs `https://repo.spring.io/milestone` repo — M-releases aren't on
  Maven Central).
- `@Tool`/`@P` → `@McpTool(name=, description=)` / `@McpToolParam(description=)`. MCP tools
  take an **explicit `name`** because they're a published network contract, not a derived
  method name. Auto-discovered by classpath scan; no manual registration.
- Same Jackson-3 gotcha: this project has no transitive Jackson 2, so
  `com.fasterxml.jackson.*` fails to compile — use `tools.jackson.*`.

**Client side (LangChain4j):**
- `langchain4j-mcp` module. Wire via `.toolProvider(mcpToolProvider)` on `AiServices`
  instead of `.tools(localBean)` — tools now come *over the network*.
- Two `ToolAssistant` beans (local `toolAssistant` + `mcpToolAssistant`) →
  `@Qualifier("mcpToolAssistant")` in the controller to disambiguate.

**The transport saga (hours lost — worth remembering):**
- Server kept coming up on the deprecated **SSE** transport
  (`WebMvcSseServerTransportProvider`). `spring.ai.mcp.server.protocol=STREAMABLE` is the
  *correct* property (verified in reference docs, must be uppercase) but appeared to have no
  effect on M6. Bumped `spring-ai-bom` M6 → **M8**.
- The startup log **never advertised** the transport switch, so it looked like M8 also
  failed. It didn't. The tell: probing endpoints showed `/sse` and `/mcp/sse` both 404, but
  `/mcp` returned **"Invalid Accept header. Expected TEXT_EVENT_STREAM"** — i.e. M8 *was*
  serving **Streamable HTTP** on a single `/mcp` endpoint (JSON or SSE by `Accept` header).
- Root cause of the client crash (`startSseChannel ... The server returned:`) was then a
  **client/transport mismatch**: `HttpMcpTransport.sseUrl(...)` is the *legacy two-endpoint*
  transport and looks for a separate SSE channel a Streamable server doesn't have. Fix: use
  **`StreamableHttpMcpTransport.Builder().url("http://localhost:8090/mcp")`** (single
  endpoint, `.url(...)` not `.sseUrl(...)`).
- **Lesson:** don't trust the startup log to tell you the transport — probe the endpoints.
  `Invalid Accept header` on `/mcp` = Streamable HTTP is live.

**Design smell noted (revisit in Phase 5):** the MCP client connects at **bean-creation
time**, so the main app won't boot unless the MCP server is already up on 8090, and one
server outage crashes startup. Ordering workaround for now; make resilient later.

---

## 12. Phase 3 — COMPLETE. Module 3 ✅

Both halves done: raw tool loop (3.0), `@Tool`/`AiServices` + three tools (Step 1–2), and a
standalone MCP server consumed cross-process (Step 3). Everything LangChain4j/MCP hides —
schema generation, `stop_reason` detection, tool dispatch, `toolUseId` matching, conversation
replay, *and now network transport* — you've seen underneath at least once.

---

## 13. Phase 4 — Multi-agent orchestration (Module 5)  ✅ COMPLETE

```
START → planner → retriever → coder → critic ─┬→ END
                                ↑             │
                                └─── RETRY ───┘
```

**LangGraph4j `org.bsc.langgraph4j:langgraph4j-core:1.8.20`** — version found by asking
DevDocs AI's own Maven tool (nice closing of the loop).

### The tutorial's Phase 4 API is almost entirely wrong

| Tutorial | Reality |
|---|---|
| `record AgentState(...) implements State` | **extend** `org.bsc.langgraph4j.state.AgentState` (wraps `Map<String,Object>`) |
| `new StateGraph<>(AgentState.class)` | `new StateGraph<>(State.SCHEMA, State::new)` |
| `Node<AgentState>` with `process()` | `node_async(state -> Map.of(...))` returning **state updates** |
| *(never mentioned)* | **Channels / reducers** — a schema declaring how each field merges |
| `RedisCheckpointSaver` | `MemorySaver`, or a separate postgres-saver module |

### The four mechanics that matter

1. **Nodes return diffs, not state.** `state` is read-only; you return a `Map` of only what
   changed. Untouched keys carry forward. That's what makes state immutable/checkpointable.
2. **Channels are merge rules.** `Channels.appender(ArrayList::new)` → new value is *added
   to a list*. **No channel declared → overwrite.** (Watch it work: a node returns a plain
   `String` for TRACE and the state shows `trace=[...]` — the channel wrapped it.)
3. **One step =** build state via the factory → run your lambda → merge the patch per
   channel → follow the edge. That's the whole engine.
4. **`node_async` / `edge_async` are ceremony** — they wrap a sync lambda to satisfy the
   async node type. No meaning for your logic.

**Conditional edges** are what make it a graph rather than a pipeline:
```java
.addConditionalEdges("critic",
        edge_async(state -> state.verdict()),   // returns a String key
        Map.of("ACCEPT", END, "RETRY", "coder"))  // key → next node
```

**⚠️ Every cycle needs an explicit termination condition you control.** `state.attempts() < 2`
in the critic is the most important line in the phase — nothing structural stops
`coder → critic → coder → …` forever. The framework's recursion limit is a backstop, not a
design.

### Building a *useful* critic is the real work

First version accepted everything, including a genuinely flawed draft. Why LLM self-critique
fails by default:
- **You told it to accept** ("accept unless there's a real error") — models weight that heavily.
- **It's grading its own homework** — same model, same blind spots.
- **Binary verdicts collapse toward lenient.**

Fixes that worked:
- **"Assume at least one problem exists"** — flips the default.
- **List problems *before* voting**, verdict on the FINAL line (chain-of-thought for critique);
  parse `lastLine`, not the first line.
- **Give the critic the subtasks** — otherwise it literally cannot check "was every subtask
  addressed," one of the criteria it was given.

*Gotcha:* after moving the verdict to the last line, the old
`reply.replaceFirst("^RETRY:?\\s*", "")` silently stops matching and `feedback` becomes the
**entire critique** — which then bloats the retry prompt. Strip from `lastLine`.

**Structured output ≠ JSON.** The planner asks for `- item` per line; with a 7B model that's
far more robust than JSON, and a malformed line is just filtered out instead of throwing.

### Local model verdict: qwen2.5:7b ran the whole graph ✅

The open risk flagged since Phase 3 (8K context + multi-agent state) **did not materialize**.

| Node | qwen2.5:7b |
|---|---|
| Planner | ✅ sensible decomposition |
| Retriever | ✅ (no LLM call) |
| Coder | ✅ drafted twice, used feedback |
| Critic | ✅ emitted RETRY then ACCEPT — both parsed correctly |

Observed budget for one retry pass: ~400 tok retrieved context + ~100 question/subtasks +
800–1500 draft + ~2000 critic prompt + ~1000 retry ≈ **4–5K of 8K**. Tight but fine. Would get
dangerous with more subtasks, `maxResults` back at 5, or the full-critique feedback bug.

Per-subtask retrieval multiplies context fast — **use `maxResults=2`, not 5**, in the retriever.

### 🔴 Major finding: cosine similarity cannot judge relevance

Asking a date-formatting question returned `sources=[devdocs-ai-java-tutorial.md, README.md]`
— and the contamination **leaked into the answer** (recommended `spring-boot-starter-webflux`
for a blocking `@RestController`, invented a `mvn -pl your-module-name` command). The model
dutifully grounded on irrelevant context.

`minScore(0.6)` changed **nothing**. Measured actual scores:

| Chunk | Score |
|---|---|
| Phase 3 MCP server task (relevant) | 0.795 |
| Production deployment doc (irrelevant) | 0.789 |
| Function-calling task (relevant) | 0.788 |
| Anthropic bean setup (relevant) | 0.785 |
| **MMUCC crash-reporting README (totally irrelevant)** | 0.760 |

**The bands overlap.** An irrelevant deployment doc (0.789) outscores two correct hits. No
threshold can separate 0.788 from 0.789. `minScore` is the wrong tool.

**Why:** cosine measures *"is this the same kind of text,"* not *"does this answer the
question."* Every chunk is English technical documentation about software — that similarity
dominates the topical difference. Embedding scores also have a **high floor**
(`nomic-embed-text` gives ~0.76 to completely unrelated text), so absolute values are nearly
meaningless; only relative ranking carries signal.

**Real fixes (→ Phase 5 hardening):**
1. **Reranker (cross-encoder)** — scores (query, chunk) *pairs*, reading them together.
   The standard answer; separation becomes obvious instead of 0.003.
2. **LLM relevance filter** — one cheap "does this help? yes/no" call per chunk. Attractive
   here since Ollama is free.
3. **Prompt around it** — tell the coder to ignore irrelevant context. Cheap, imperfect,
   would have prevented the WebFlux contamination.

**Also: corpus hygiene is part of RAG quality.** An MMUCC crash-reporting README from an old
ingestion test is now permanently polluting every query. Needs a cleanup pass.

### Checkpointing ✅

Tutorial's `RedisCheckpointSaver` doesn't exist as written. Real options: **`MemorySaver`**
and **`FileSystemSaver`** (both in core), `PostgresSaver`, plus separate modules for
MySQL/Oracle/Redis.

```java
var checkpointSaver = new MemorySaver();
var compileConfig = CompileConfig.builder().checkpointSaver(checkpointSaver).build();
return graph.compile(compileConfig);

// invoke with a thread ID
var config = RunnableConfig.builder().threadId(threadId).build();
app.invoke(Map.of(AgentGraphState.QUERY, question), config);
```

- **`threadId` isolates execution contexts** (one user session / conversation). Checkpoints
  are stored per thread, so concurrent users don't collide.
- Inspect history via `checkpointSaver.list(config)`. Verified output — the full traversal,
  newest first:
  ```
  critic → __END__ | coder → critic | retriever → coder | planner → retriever | __START__ → planner
  ```
- **`MemorySaver` dies with the JVM.** Use `FileSystemSaver` (core, no extra dep) for real
  resumption across restarts — the practical stand-in for the tutorial's Redis ambition.
- **`CompileConfig.interruptBefore("critic")`** stops the graph and returns partial state;
  re-invoking with the same `threadId` resumes from the checkpoint. That's the
  human-in-the-loop pattern — the most compelling reason checkpointing exists.

**Phase 4 COMPLETE.**

---

## 14. Phase 5 — Production hardening (Module 7)  🔄 IN PROGRESS

**Sequencing note:** MCP client resilience came first because it was a *blocker* — the client
connected at bean-creation time, so any `@SpringBootTest` failed unless port 8090 was live.

**⚠️ The tutorial's guardrail code won't compile here.** It uses `HandlerInterceptor` +
`HttpServletRequest` — that's Spring **MVC**. The main app is **WebFlux**, so guardrails need
a `WebFilter`. (Ironically the MCP server *is* MVC, where the tutorial's code would work.)

### Step 1 — MCP client resilience ✅

`@Lazy` alone **doesn't work**: `McpToolProvider.builder().mcpClients(...)` calls `listTools()`
at build time to discover tools, which resolves the proxy and throws. Fix — build the client
*inside* the provider bean and catch:

```java
@Bean
public ToolProvider mcpToolProvider() {
    try {
        // build transport + DefaultMcpClient + McpToolProvider here
        System.out.println(">>> MCP server connected at :8090");
        return provider;
    } catch (Exception e) {
        System.out.println(">>> MCP server UNAVAILABLE — tool endpoints run without remote tools");
        return request -> ToolProviderResult.builder().build();   // empty = "I have no tools"
    }
}
```
Delete the separate `mcpClient` bean so nothing else can trigger a connection. `ToolProvider`
is a functional interface, so an empty `ToolProviderResult` is a valid no-op implementation and
`AiServices` handles it fine.

Also set `logRequests(false)` / `logResponses(false)` — those were flooding the console with
MCP ping traffic.

**Honest tradeoff:** the service stays up but answers quietly get *worse* (no live Maven data).
Production would surface that — health indicator or a response flag — not just log it at startup.

### Step 2a — Retrieval eval (deterministic) ✅

No LLM judge needed — retrieval has ground truth. 5 fixtures asserting the right *source*
appears in the top 5. **All 5 pass**, including the two tutorial-doc fixtures competing against
78 similar chunks.

```java
@SpringBootTest
class RetrievalEvalTest {
    @Autowired RetrievalService retrievalService;
    record Fixture(String question, String expectedSourceFragment) { … }

    @ParameterizedTest @MethodSource("fixtures")
    void retrievesExpectedSource(Fixture f) {
        var result = retrievalService.retrieve(f.question(), 5);
        assertThat(result.sources()).anyMatch(s -> s.contains(f.expectedSourceFragment()));
    }
}
```

**Deliberately lenient** — asserts the right doc appears *somewhere* in top-5, not that it ranks
first. Given the 0.788-vs-0.789 finding, asserting rank would fail constantly. Consequence: this
test would still pass with a reranker that dramatically improved ordering, so it *can't measure*
that improvement. Tighten once a reranker lands.

### Step 2b — LLM-as-judge eval ✅

3 fixtures scoring the agent graph's answers 0–10, asserting ≥7.

**🔴 The judge was the broken component.** A technically correct, well-explained `@Transactional`
answer (worth ~9) was scored **0**. Raw reply was literally `[0]` — not a parse bug, the model
genuinely returned zero.

**This is the Phase 4 critic failure in mirror image.** There, a bare `ACCEPT`/`RETRY` verdict
collapsed toward *lenient*; here, a bare integer collapsed toward *harsh*. Same root cause:
**a small model asked for a naked structured verdict with no reasoning produces garbage.**

Fix — same as the critic: reason first, labeled marker on the final line.
```java
First, briefly state whether the answer conveys: %s
Then state whether anything is factually wrong.
Then output your score on the FINAL line as: SCORE: <integer 0-10>
```
```java
var m = Pattern.compile("SCORE:\\s*(\\d+)").matcher(raw);
int score = m.find() ? Integer.parseInt(m.group(1)) : -1;   // -1 = unparseable, NOT 0
```
**All 3 now pass.**

**Eval-harness gotchas:**
- **Always print the judge's raw reply.** Without it you cannot distinguish "the answer is bad"
  from "we couldn't read the judge." That debug line is what disproved the parse-bug theory.
- **Use `-1` for unparseable, never `0`** — conflating them makes the suite lie to you.
- `.as(...)` must print the full answer on failure; "expected 7 but was 4" is useless alone.
- Naive `replaceAll("[^0-9]","")` turns "8 out of 10" into `810`. Extract the *first* match after
  a labeled marker.
- **The eval harness is as much a bug source as the system under test.** Same shape as the
  Phase 3 tool lesson.

**Known weakness:** the judge is the *same* `ChatModel` bean the graph uses — grading its own
homework, exactly the Phase 4 critic problem. The dual-provider setup makes the fix easy: pin a
dedicated judge bean to Anthropic while `llm.provider=ollama`, so Claude grades qwen.

**Also:** `spring-boot-starter-webflux-test` **does exist** in Boot 4 (pulls in
`spring-boot-starter-test` transitively) — no extra test dependency needed.
*Gotcha:* a test file placed in `src/main/java` fails to resolve JUnit/AssertJ/`@SpringBootTest`
all at once, because test-scope deps are invisible to main-source compilation.

### Option A chosen: eval against the running local stack

Rejected TestContainers for now. A fresh pgvector is an **empty** pgvector — every RAG eval
would fail without first ingesting a known corpus, so TestContainers makes backlog item #3
(reproducible corpus) mandatory rather than solving it. Deeper tension: **embeddings are local**,
and GitHub Actions has no Ollama — fully reproducible cloud CI would require a hosted embedding
provider, undoing the offline goal.

### Step 3 — Guardrails as a `WebFilter` ✅

**Reading the request body in a WebFilter is genuinely awkward in WebFlux** — the body is a
one-shot reactive stream, so reading it in a filter leaves the controller with nothing. You must
buffer it and re-wrap via `ServerHttpRequestDecorator`. This is the main reason the tutorial's
MVC code doesn't translate.

Blocks prompt-injection phrases and PII (SSN / card patterns) before they reach the model.

**🔴 Two WebFlux traps hit here:**
1. **Never `switchIfEmpty` on a `Mono<Void>`.** `chain.filter(...)` returns `Mono<Void>`, which
   *always completes empty* — so `switchIfEmpty(chain.filter(exchange))` fires on **every
   successful request**, running the chain a second time against an already-consumed body.
   Symptom: `ServerWebInputException: 400 "No request body" … but ServerHttpResponse already
   committed (200 OK)`. Fix: `defaultIfEmpty(new byte[0])` on the **bytes**, upstream of the
   chain call, so `chain.filter(...)` runs exactly once on every path.
2. **`Flux.just(buffer)` is single-use.** A `DataBuffer` is consumed when read, so a second
   subscriber gets zero bytes. Use `Flux.defer(() -> Flux.just(bufferFactory().wrap(bytes)))` to
   build a fresh buffer per subscription.

**Honest limitations (verified by test):**
- Keyword blocklists lose to paraphrase — "Disregard prior directives" sails through while
  "ignore previous instructions" is blocked.
- The card regex `\b(?:\d[ -]*?){13,16}\b` false-positives on any long digit run.
- **What it's actually good for:** the accidental case — a user who genuinely pastes an SSN
  without thinking. Real, common, worth blocking.
- The serious version is a classifier model: one cheap LLM call scoring the input for injection
  intent and PII, which handles paraphrase because it reads *meaning*, not strings.

### Step 4 — Local observability ✅

Chose **local metrics only** (Actuator + Micrometer), no Langfuse/OTLP export — keeps the
offline goal intact. `management.endpoints.web.exposure.include=health,metrics`.

Instrument each graph node with one metric name + a `node` tag (so nodes are comparable, rather
than four unrelated metrics):
```java
Timer.Sample sample = Timer.start(meterRegistry);
try { /* node body */ }
finally { sample.stop(meterRegistry.timer("devdocs.agent.node", "node", "planner")); }
```

**🔴 Measured result — the critic is the most expensive node:**

| Node | Time | Share |
|---|---|---|
| retriever | 0.27s | 1% |
| planner | 2.80s | 11% |
| coder | 9.08s | 36% |
| **critic** | **13.33s** | **53%** |

**The reviewer costs more than the writer.** The critic's prompt carries question + subtasks +
the *full draft*, so it processes more input than any other node. A RETRY doubles the whole
coder+critic block (~22s more). Levers if latency ever matters: truncate what the critic sees,
or add a conditional edge that skips the critic for simple questions.

**Also important: retrieval is 1% of the run.** All the retrieval-quality work is essentially
free latency-wise — enormous headroom for a reranker or relevance filter. Worth knowing *before*
optimizing the wrong thing.

### Step 5 — Reproducible corpus ✅

`POST /api/ingest/reset?confirm=true` wipes `doc_embeddings` and reseeds a **declared** baseline
in code. The `confirm` guard prevents a destructive endpoint firing on a stray curl. Evals
confirmed green against the rebuilt corpus — which is the actual proof it works.

*Improvement:* make corpus file paths a property (`devdocs.corpus.files=…`) so the whole corpus
is declarative rather than partly machine-specific.

### Step 6 — CI gate ⏭️ SKIPPED (not needed near-term)

Blocker if revisited: **evals can't run on GitHub Actions** — they need Ollama (no GPU on
runners, ~9GB model pull) and a seeded pgvector. The tutorial sidesteps this by assuming OpenAI
embeddings; the offline choice is what makes it hard. Options were: (A) CI builds + unit tests
only, evals stay local; (B) CI runs evals against Anthropic + hosted embeddings — automated but
tests a *different configuration* than you develop against; (C) self-hosted runner. **A** is the
honest choice for a portfolio project.

### Step 7 — Semantic cache ⏭️ DEFERRED

Reasoning worth keeping: a semantic cache decides "close enough" using **cosine similarity** —
the exact mechanism measured as unable to separate 0.788 from 0.789. And unlike weak retrieval
(where the model can ignore bad context), a false cache hit returns a **completely wrong answer
with total confidence**. The tutorial's `SIMILARITY_THRESHOLD = 0.92` does all the work, and on
`nomic-embed-text` (floor ~0.76) that number needs measuring, not assuming. Safer alternative:
an **exact-match cache** on the normalized question — zero false positives, catches the common
repeat-question case, needs no Redis.

### Step 8 — Reranker ✅  🔴 The headline result

**Cross-encoder reranking fixed the retrieval-relevance problem outright.**

LangChain4j has a `ScoringModel` abstraction with a local ONNX implementation
(`OnnxScoringModel`, CPU by default) — fits the offline constraint. Implemented the **LLM-as-
reranker** variant instead (no new dependency, no model download, and it has the property that
matters: it reads query and chunk *together*). Swappable for ONNX later.

Architecture change: **retrieve wide, rerank narrow** — pull `maxResults * 3` from vector +
keyword search, score each, keep the top N above a floor. Affordable because retrieval is 1% of
run time.

**Measured on the date-formatting question that previously produced contaminated answers:**

| Chunk | Cosine | Cross-encoder |
|---|---|---|
| Guardrail/Hardening task | 0.771 | **0** |
| "Last updated April 2026" footer | 0.759 | **0** |
| Anthropic API docs table row | 0.765 | **0** |
| MCP server task | 0.764 | **0** |
| **HikariCP connection pooling** | **0.807** | **0** |
| Spring Boot scaffold task | 0.801 | **2** |

Cosine spread everything across 0.759–0.807 — a **0.05 band** covering chunks from "document
footer" to "connection pool config." The cross-encoder collapsed all of it to 0–2. Note the
HikariCP chunk scored *highest* on cosine and **zero** on rerank: reading it alongside a
date-formatting question makes the irrelevance obvious.

Result: `0 chars, 0 sources`, empty context, and the coder answered from its own knowledge
correctly — **the WebFlux contamination from Phase 4 is gone at the root.**

**The principle, now measured twice:** a bi-encoder compares two independent vectors and can only
tell you *"same kind of text"*; a cross-encoder reads the pair together and can tell you
*"answers this question."* The 0.788-vs-0.789 finding was the symptom; this is the cure.

**House rule established (needed three times now):** reason first, then emit a labeled verdict on
the final line — critic node, LLM judge, and reranker all failed without it and worked with it.

### Remaining

- Semantic cache (deferred — see above)
- CI gate (skipped)
- ONNX cross-encoder to replace the LLM reranker (faster, same principle)
- Tighten `RetrievalEvalTest` to assert *rank*, now that reranking makes that meaningful

---

*Last updated: July 2026. Keep alongside `devdocs-ai-java-tutorial.md`.*
