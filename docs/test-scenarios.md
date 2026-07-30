# Marion DMV — Test Scenarios

Each scenario covers a distinct code path. Use these against the Angular UI at
`http://localhost:4200` or directly against `POST /api/transfer/query/agent`.

---

## Group A — Tax Computation (STEP 2 happy path)

### A1 — Zero additional tax (reciprocity credit ≥ Marion tax)

| Field | Value |
|---|---|
| Scenario | `Customer purchased a vehicle for $15,000. They paid 6% sales tax in Crestwood. What documents are required and how much additional Marion tax is owed?` |
| Origin State | `Crestwood` |
| Transfer Type | `PURCHASE` |
| County | `Marion County` |

**Expected:** `supervisorReferral=false`, `taxOwed=0.00`

**Why:** Marion tax = 5.5% × $15k = $825. Crestwood credit = 6% × $15k = $900. `min($900, $825) = $825`. Additional = $0. Exercises the credit-cap branch.

---

### A2 — Partial reciprocity credit

| Field | Value |
|---|---|
| Scenario | `Customer paid 4.5% sales tax in Halloway on an $18,000 vehicle purchase. Marion's rate is 5.5%. How much additional Marion sales tax is owed?` |
| VIN | `1HAL0000001000002` |
| Origin State | `Halloway` |
| Transfer Type | `PURCHASE` |
| County | `Marion County` |

**Expected:** `supervisorReferral=false`, `taxOwed=180.00`

**Math:** Marion = $990, Halloway paid = $810, credit = $810, owed = $180. Exercises the partial-credit branch and the MCP rate banner injection (Halloway rate 4.5% from DB).

---

### A3 — No reciprocity agreement (full Marion tax)

| Field | Value |
|---|---|
| Scenario | `Customer purchased a vehicle for $20,000 in Pembrook. No tax was collected by Pembrook. How much Marion sales tax is owed?` |
| VIN | `1PMB0000001000001` |
| Origin State | `Pembrook` |
| Transfer Type | `PURCHASE` |
| County | `Marion County` |

**Expected:** `supervisorReferral=false`, `taxOwed=1100.00`

**Why:** Pembrook has no reciprocity agreement → credit = $0 → full 5.5% × $20k = $1,100. Also exercises the odometer-field scanning caution (VIN record has `odometer` field that must NOT trigger STEP 1).

---

### A4 — Close rates (Verdana 5% vs Marion 5.5%)

| Field | Value |
|---|---|
| Scenario | `Customer paid 5% sales tax in Verdana on a $20,000 vehicle purchase. Marion's rate is 5.5%. How much additional Marion sales tax is owed?` |
| VIN | `1VRD0000001000001` |
| Origin State | `Verdana` |
| Transfer Type | `PURCHASE` |
| County | `Marion County` |

**Expected:** `supervisorReferral=false`, `taxOwed=100.00`

**Why:** Marion = $1,100, Verdana paid = $1,000, credit = $1,000, owed = $100. Tests the near-equal-rate case where the model is most likely to use the wrong differential formula.

---

## Group B — Supervisor Referral (STEP 1 triggers)

### B1 — Active paper lien

| Field | Value |
|---|---|
| Scenario | `A customer presents a Crestwood title showing an unreleased lien held by Midwest Auto Finance. What does the examiner do?` |
| VIN | `1CST0000001000003` |
| Origin State | `Crestwood` |
| Transfer Type | `PURCHASE` |
| County | `Marion County` |

**Expected:** `supervisorReferral=true`, `referralForm="TR-10"`, `checklist=null`, `taxOwed=null`, `conditionalChecklist` non-empty

**Exercises:** STEP 1 lien trigger from MCP VEHICLE_RECORD (`"lien_status":"ACTIVE"`).

---

### B2 — Branded title: Halloway "Rebuilt" → Marion "Reconstructed"

| Field | Value |
|---|---|
| Scenario | `A customer presents a Halloway title with the brand 'Rebuilt'. What brand should appear on the Marion title and what does the examiner do?` |
| VIN | `1HAL0000001000001` |
| Origin State | `Halloway` |
| Transfer Type | `RELOCATION` |

**Expected:** `supervisorReferral=true`, `referralReason` contains "Reconstructed" (NOT "Rebuilt")

**Exercises:** Brand banner injection (`*** BRAND STAMP DETECTED ***`), STEP 1 brand trigger, state-specific equivalency (Halloway "Rebuilt" ≠ Verdana "Rebuilt"). This is the hardest distractor in the corpus.

---

### B3 — Branded title: Verdana "Rebuilt" → Marion "Rebuilt"

| Field | Value |
|---|---|
| Scenario | `What is the examiner required to do when a customer presents a Verdana vehicle title that carries a 'Rebuilt' brand stamp? Which Marion brand equivalent applies?` |
| VIN | `1VRD0000001000003` |
| Origin State | `Verdana` |
| Transfer Type | `PURCHASE` |
| County | `Marion County` |

**Expected:** `supervisorReferral=true`, `referralReason` contains "Rebuilt" (NOT "Reconstructed")

**Exercises:** Same brand word as B2, opposite equivalency — validates that the model uses the correct state profile, not the Halloway one.

---

### B4 — Active ELT lien

| Field | Value |
|---|---|
| Scenario | `A customer's vehicle is titled in Verdana as an ELT record and shows an active lien. What is the process?` |
| VIN | `1VRD0000001000002` |
| Origin State | `Verdana` |
| Transfer Type | `RELOCATION` |
| County | `Marion County` |

**Expected:** `supervisorReferral=true`, `referralForm="TR-10"`, `checklist=null`, `taxOwed=null`, `conditionalChecklist` non-empty

**Exercises:** ELT title form + active lien (both are STEP 1 triggers); the reasoning should explain that the lien must be released electronically in Verdana's system before Marion can request the ELT conversion.

---

### B5 — Halloway "Junk" brand → Marion "Salvage"

| Field | Value |
|---|---|
| Scenario | `A customer presents a Halloway paper title with the brand 'Junk'. What happens and what brand appears on the Marion title?` |
| Origin State | `Halloway` |
| Transfer Type | `PURCHASE` |
| County | `Marion County` |

**Expected:** `supervisorReferral=true`, reasoning mentions "Salvage" as the Marion equivalent

**Exercises:** STEP 1 "Junk" trigger without MCP VIN (question-text scan only); Halloway "Junk" → Marion "Salvage" mapping from the Brand Equivalency Guide.

---

## Group C — Emissions (STEP 2 branching)

### C1 — Emissions required (metro county, vehicle < 25 years)

| Field | Value |
|---|---|
| Scenario | `A customer is registering a 2003 model year vehicle in Marion County. The current year is 2026. Is emissions testing required?` |
| Origin State | `Crestwood` |
| County | `Marion County` |
| Transfer Type | `PURCHASE` |

**Expected:** `supervisorReferral=false`, checklist contains "Emissions inspection (Form EMIT-1)"

**Math:** 2026 − 2003 = 23 < 25 → REQUIRED. Exercises the current 25-year rule and validates that the superseded 20-year rule is not used.

---

### C2 — Emissions exempt (vehicle ≥ 25 years)

| Field | Value |
|---|---|
| Scenario | `Customer is transferring a 1995 model year Crestwood paper title with no lien into Marion County. What documents are needed?` |
| Origin State | `Crestwood` |
| County | `Marion County` |
| Transfer Type | `PURCHASE` |

**Expected:** `supervisorReferral=false`, checklist does NOT contain any emissions item

**Math:** 2026 − 1995 = 31 ≥ 25 → exempt. Validates the exemption branch.

---

### C3 — Emissions exempt (rural county)

| Field | Value |
|---|---|
| Scenario | `A customer purchased a 2020 vehicle from Crestwood and will register it in Jefferson County. Is emissions testing required?` |
| Origin State | `Crestwood` |
| County | `Jefferson County` |
| Transfer Type | `PURCHASE` |

**Expected:** `supervisorReferral=false`, no emissions item in checklist

**Why:** Jefferson County is rural (not Marion, Riverside, or Capital) → always exempt regardless of vehicle age.

---

### C4 — Emissions distractor: Halloway has no program but Marion rules apply

| Field | Value |
|---|---|
| Scenario | `A vehicle was previously titled in Halloway, which has no emissions testing program. The customer is registering a 2003 model year vehicle in Marion County. Is emissions testing required?` |
| Origin State | `Halloway` |
| County | `Marion County` |
| Transfer Type | `PURCHASE` |

**Expected:** `supervisorReferral=false`, checklist DOES contain emissions inspection

**Why:** Halloway's lack of an emissions program is irrelevant — Marion's rules govern the Marion registration. This tests that the model does not confuse "origin state has no program" with "exempt in Marion."

---

## Group D — PII Guardrail

### D1 — SSN in request body

Send this directly (not through the UI, which doesn't expose raw JSON):

```bash
curl -X POST http://localhost:8080/api/transfer/query/agent \
  -H "Content-Type: application/json" \
  -d '{"question":"My SSN is 078-05-1120. What documents do I need for a Crestwood transfer?","originState":"Crestwood"}'
```

**Expected:** HTTP 400 `{"error":"PII_DETECTED","piiType":"SSN"}` — request never reaches the controller.

---

### D2 — Credit card number in request body

```bash
curl -X POST http://localhost:8080/api/transfer/query/agent \
  -H "Content-Type: application/json" \
  -d '{"question":"My card 4111 1111 1111 1111 was used to pay. What forms do I need?","originState":"Pembrook"}'
```

**Expected:** HTTP 400 `{"error":"PII_DETECTED","piiType":"CREDIT_CARD"}`.

---

## Group E — Retrieval Quality (distractor / superseded rule)

### E1 — Superseded emissions rule (20-year vs 25-year)

| Field | Value |
|---|---|
| Scenario | `At what model year age does a vehicle become exempt from Marion emissions testing?` |
| County | `Marion County` |

**Expected:** Response states 25 years (current rule, effective Jan 2023). If the answer says 20 years, the reranker is failing to penalise the superseded `admin-rule-2-4-emissions-superseded.md`.

---

### E2 — Fee lookup (current vs superseded schedule)

| Field | Value |
|---|---|
| Scenario | `What is the current fee for an out-of-state title transfer application in Marion?` |

**Expected:** Current fee amounts from `admin-rule-9-fee-schedule.md`, not the pre-2023 superseded schedule.

---

## Group F — Parse / Error Paths

### F1 — Normal request (verify 200 + JSON body)

```bash
curl -X POST http://localhost:8080/api/transfer/query/agent \
  -H "Content-Type: application/json" \
  -d '{"question":"Clean paper title from Crestwood, no lien, $12,000 purchase price.","originState":"Crestwood","county":"Marion County","transferType":"PURCHASE"}' \
  -w "\n\nHTTP %{http_code}"
```

**Expected:** HTTP 200, body is a JSON object with all `TransferResponse` fields.

---

### F2 — Agent error path (MCP server down)

Stop the MCP server (`Ctrl-C` on its terminal) then submit any scenario with a VIN.

**Expected:** HTTP 200 with MCP tool data absent (graceful degradation — `McpToolService` returns `Optional.empty()` on connection failure); brand banner and rate banner will not appear in the prompt. Response is still generated from retrieved context alone.

---

## Group G — Human-in-the-Loop Supervisor Review

Exercises the LangGraph4j checkpoint pause/resume on `/api/transfer/query/agent` and
`/api/transfer/query/agent/resume`. Only the agent endpoint has a checkpointer —
`/api/transfer/query` and `/api/transfer/stream` never pause, since they don't run the graph.

### G1 — Approve feeds the decision back to the model and produces a resolved transfer

Reuses the B2 scenario. Through the UI: submit it, confirm the amber "Awaiting Supervisor
Decision" card appears under the red referral banner, add a note, click **Approve**.

Direct (writes the resume body to a file rather than shell-interpolating it — the note text's
punctuation, e.g. em dashes, is exactly the kind of thing that breaks naive `-d "...$VAR..."`
quoting; also extracts `threadId` with `grep`/`sed` rather than `python3 -c`, since a native
Windows `python3` and Git Bash's `/tmp` can resolve to different filesystems):

```bash
curl -s -X POST http://localhost:8080/api/transfer/query/agent \
  -H "Content-Type: application/json" \
  -d '{"question":"A customer presents a Halloway title with the brand '"'"'Rebuilt'"'"'. What brand should appear on the Marion title and what does the examiner do?","vehicleVin":"1HAL0000001000001","originState":"Halloway","transferType":"RELOCATION","county":"Marion County"}' \
  | tee /tmp/g1-pause.json

THREAD=$(grep -o '"threadId":"[^"]*"' /tmp/g1-pause.json | sed 's/.*:"//;s/"$//')

cat > /tmp/g1-resume-body.json << 'EOF'
{"threadId":"REPLACE_ME","decision":"APPROVED","note":"Reconstructed brand confirmed against title photo, cleared to proceed."}
EOF
sed -i "s/REPLACE_ME/$THREAD/" /tmp/g1-resume-body.json

curl -s -X POST http://localhost:8080/api/transfer/query/agent/resume \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/g1-resume-body.json
```

**Expected:**
- First response: `awaitingSupervisorDecision=true`, `response.supervisorReferral=true`, `checklist=null`, a `threadId` present.
- Second response: `awaitingSupervisorDecision=false`, same `threadId`, `response.supervisorReferral=false`.
- `response.checklist` is now populated — items from the first response's `conditionalChecklist`
  folded in, plus anything else STEP 2 requires (fees, `taxOwed` computed).
- `response.reasoning` explicitly references the supervisor's approval (and note, if one was given)
  — it is **not** the same text as the first response. This is a genuine second LLM call: check
  `GET /actuator/metrics/marion.agent.node?tag=node:generate` COUNT increases by exactly 1 more
  after resume (2 total for one referral+decision cycle), confirming GENERATE really ran twice —
  once to find the exception, once to finalize after the supervisor's ruling.

---

### G2 — Deny keeps the referral blocked, but folds the reason into the record

Same setup as G1 but `"decision":"DENIED"`. **Expected:**
- `awaitingSupervisorDecision=false`, `response.supervisorReferral=true` (still — the transfer
  remains blocked, it's just no longer *pending*), `checklist=null`, `taxOwed=null`,
  `conditionalChecklist=null` (there is nothing left to condition on — the case is closed as denied).
- `response.conditionalNote` explains the transfer was denied, incorporating the supervisor's note.
- `response.reasoning` states plainly that the supervisor denied the referral and why.

**Risk anticipated while building this (not yet observed failing, but worth watching in the eval
suite):** the post-review prompt still contains the *original* STEP 1 trigger banner (e.g.
`*** BRAND STAMP DETECTED ... supervisorReferral=true REQUIRED ***`) earlier in the same prompt,
with the supervisor's decision appended later. Given this project's documented qwen2.5:7b
contamination issues elsewhere (PROJECT.md gotchas table), a weaker model could plausibly re-trigger
STEP 1 from the earlier banner instead of honoring the later decision. Pre-emptively guarded with an
explicit override line at the top of the supervisor-review block: *"this supersedes any STEP 1
trigger banners above — do not re-evaluate STEP 1."* Both G1 (Approve) and G2 (Deny) passed
correctly with the override in place on the first live run — if this ever regresses, that banner
conflict is the first thing to suspect.

---

### G3 — Resuming an unknown or already-restarted threadId fails loudly

```bash
curl -s -X POST http://localhost:8080/api/transfer/query/agent/resume \
  -H "Content-Type: application/json" \
  -d '{"threadId":"00000000-0000-0000-0000-000000000000","decision":"APPROVED"}' \
  -w "\n\nHTTP %{http_code}"
```

**Expected:** HTTP 500 `{"error":"AGENT_ERROR","detail":"Missing Checkpoint!"}` — `MemorySaver` has
no record of this thread. This is the concrete, testable face of the "in-process only" limitation:
restart `marion-app` between G1's pause and resume steps and the real `threadId` will fail exactly
this way too.

---

### G4 — Clean transfers never pause (regression check)

Re-run A1 (or any Group A/C scenario) through `/query/agent`. **Expected:**
`awaitingSupervisorDecision=false` on the very first response, no `await_supervisor` step visible in
`marion.agent.node` metrics (only `retrieve`, `tool-fetch`, `generate` accrue time) — confirms the
conditional edge only routes to the gate node when `supervisorReferral=true`, never on the happy path.

---

## Metrics Verification

After running several scenarios, check node latency breakdown:

```
GET http://localhost:8080/actuator/metrics/marion.agent.node?tag=node:retrieve
GET http://localhost:8080/actuator/metrics/marion.agent.node?tag=node:tool-fetch
GET http://localhost:8080/actuator/metrics/marion.agent.node?tag=node:generate
```

Each returns Micrometer `Timer` statistics (count, total time, max). Typical profile:
- `generate` dominates (LLM inference on qwen2.5:7b takes 15–60s)
- `retrieve` is next (embedding call + two DB queries + rerank LLM call)
- `tool-fetch` is fastest (3 synchronous JDBC calls via MCP)
