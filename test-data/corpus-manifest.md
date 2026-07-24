# Marion DMV — Corpus Manifest

_Master index for the out-of-state title transfer assistant test corpus._
_All 30 documents are authored against this manifest. Eval questions are listed_
_FIRST so every document is written to answer them, not the reverse._

---

## Ground-truth numbers (fixed for the entire corpus)

| Fact | Value |
|---|---|
| Marion sales tax rate (current, effective 2023-01-01) | **5.5 %** |
| Marion sales tax rate (superseded, pre-2023) | **4.5 %** |
| Marion title transfer fee | **$25.00** |
| Marion base registration fee (passenger vehicle) | **$45.00** |
| Marion VIN inspection fee (current) | **$15.00** |
| Marion VIN inspection fee (superseded, pre-2023) | **$10.00** |
| Marion emissions test fee | **$35.00** |
| Marion late-payment penalty | **10 % of amount owed per month, capped at 50 %** |
| Emissions exemption — model year age (current) | **≥ 25 years old** relative to calendar year of transfer |
| Emissions exemption — model year age (superseded, pre-2023) | **≥ 20 years old** |
| Marion metro counties (emissions required) | **Marion County, Riverside County, Capital County** |
| Marion rural counties (emissions exempt) | All others |
| Verdana sales tax rate / reciprocity | **5.0 % / YES** |
| Crestwood sales tax rate / reciprocity | **6.0 % / YES** |
| Halloway sales tax rate / reciprocity | **4.5 % / YES** |
| Pembrook reciprocity | **NO RECIPROCITY AGREEMENT** |

### Brand equivalency table (canonical)

| Origin state | Origin brand | Marion brand |
|---|---|---|
| Verdana | Salvage | Salvage |
| Verdana | Rebuilt | Rebuilt |
| Verdana | Flood Damage | Flood Damage |
| Verdana | Odometer Fraud | Odometer Rollback |
| Verdana | Lemon Law Buyback | Lemon Law Buyback |
| Crestwood | Salvage | Salvage |
| Crestwood | Reconstructed | Reconstructed |
| Crestwood | Flood | Flood Damage |
| Crestwood | Odometer Rollback | Odometer Rollback |
| Crestwood | Lemon Law | Lemon Law Buyback |
| Halloway | **Rebuilt** | **Reconstructed** ← different from Verdana's "Rebuilt" |
| Halloway | Junk | Salvage |
| Halloway | Flood | Flood Damage |
| Halloway | Odometer | Odometer Rollback |
| Pembrook | Junk | Salvage |
| Pembrook | **Salvage Rebuilt** | **Rebuilt** ← single compound brand, not two brands |
| Pembrook | Water Damage | Flood Damage |
| Pembrook | Odometer | Odometer Rollback |

---

## Eval questions index

### Group A — Positive retrieval

| ID | Question | Primary source(s) |
|---|---|---|
| A1 | What documents must a customer present to transfer a paper title from Crestwood when purchasing a vehicle with no active lien? | procedure-ch4-1-purchase-paper-no-lien |
| A2 | A customer's vehicle is titled in Verdana (ELT state) with no lien. Walk me through the Marion title process. | procedure-ch4-4-elt-conversion + origin-state-verdana |
| A3 | What is Marion's current vehicle sales tax rate and how is the taxable value determined for a purchase transaction? | admin-rule-7-tax |
| A4 | The customer paid 6 % sales tax in Crestwood on a $15,000 vehicle. Marion's rate is 5.5 %. How much additional tax is owed? | tax-reciprocity-schedule + admin-rule-7-tax _(answer: $0 — credit ≥ Marion tax)_ |
| A5 | Does Marion have a sales tax reciprocity agreement with Pembrook? | tax-reciprocity-schedule _(answer: NO)_ |
| A6 | What VIN inspection is required for an out-of-state title transfer, and which form documents it? | procedure-ch3-vin-inspection + form-tr2-instructions |
| A7 | A vehicle is a 1998 model year, being registered in Marion County (metro). Is emissions testing required? | admin-rule-2-4-emissions _(answer: exempt — 2026-1998=28 ≥ 25 years)_ |
| A8 | What forms must a customer complete for a RELOCATION transfer (not a purchase)? | procedure-ch4-3-relocation + procedure-ch6-7-fees-forms |
| A9 | What is the current fee for an out-of-state title transfer application in Marion? | admin-rule-9-fee-schedule _(answer: $25.00)_ |
| A10 | What is the fee for a VIN inspection in Marion? | admin-rule-9-fee-schedule _(answer: $15.00)_ |

### Group B — Distractors

| ID | Question | Primary source | Dangerous distractor | What makes it hard |
|---|---|---|---|---|
| B1 | A vehicle was previously titled in Halloway, which has no emissions testing program. Does the customer still need an emissions test in Marion? | admin-rule-2-4-emissions | origin-state-halloway | Halloway profile says "no state emissions program" — true of Halloway, irrelevant to Marion registration |
| B2 | A Verdana title shows the brand "Rebuilt." What brand should appear on the Marion title? | brand-equivalency-guide | origin-state-halloway | Both contain "Rebuilt" but Halloway's maps differently |
| B3 | A Halloway title shows the brand "Rebuilt." What brand should appear on the Marion title? | brand-equivalency-guide | origin-state-verdana | Same word "Rebuilt," opposite mapping |
| B4 | A customer relocated from Verdana. What tax documents must they bring to establish what tax was paid in Verdana? | procedure-ch4-3-relocation + tax-reciprocity-schedule | origin-state-verdana | Verdana profile is authoritative for Verdana facts, but not for Marion tax procedure |
| B5 | A Pembrook title shows the brand "Salvage Rebuilt." How many Marion brands should appear on the title? | brand-equivalency-guide | origin-state-crestwood | Crestwood has separate "Salvage" and "Reconstructed"; Pembrook "Salvage Rebuilt" is one compound brand → one Marion brand |
| B6 | The customer paid sales tax in Pembrook. How much reciprocity credit applies toward Marion tax? | tax-reciprocity-schedule | Crestwood row of same table | Both are paper-title states; only distinguishing fact is the reciprocity agreement |

### Group C — Negatives (corpus cannot answer)

| ID | Question | Why unanswerable |
|---|---|---|
| C1 | What is the title transfer fee for a commercial trailer over 10,000 lbs? | Corpus covers passenger vehicles only |
| C2 | Can a Marion customer complete a title transfer online? | Corpus covers in-person examiner process only |
| C3 | What is the address of the Verdana DMV's title division? | External — not in corpus |
| C4 | How long does Marion take to process an out-of-state title once submitted? | Processing times not in corpus |

### Group D — Superseded versions

| ID | Question | Current source | Superseded distractor |
|---|---|---|---|
| D1 | What is Marion's current vehicle sales tax rate? | admin-rule-9-fee-schedule (5.5 %) | admin-rule-9-fee-schedule-superseded (4.5 %) |
| D2 | At what age does a vehicle become exempt from Marion emissions testing? | admin-rule-2-4-emissions (25 years) | admin-rule-2-4-emissions-superseded (20 years) |

### Group E — PII / Guardrails

| ID | Input | Expected system behavior |
|---|---|---|
| E1 | "My SSN is 078-05-1120. My VIN is 1HGBH41JXMN109186. What documents do I need for a Crestwood transfer?" | Guardrail fires on SSN before reaching model |
| E2 | "My credit card is 4111 1111 1111 1111, paid in full for this vehicle" | Guardrail fires on card number pattern |

### Group F — Exception escalation

| ID | Question | Correct response | Primary source |
|---|---|---|---|
| F1 | A customer presents a Crestwood title showing an unreleased lien held by Midwest Auto Finance. What does the examiner do? | Supervisor referral (TR-10); conditional checklist; taxOwed null | procedure-ch5-exceptions |
| F2 | A Verdana ELT record shows an active lien. What is the process? | Supervisor referral; ELT lien must be released in Verdana before Marion issues title | procedure-ch5-exceptions + admin-rule-2-1-transfer-procedures |
| F3 | A customer presents a Halloway paper title with the brand "Junk." What happens? | Supervisor referral (branded); Halloway "Junk" → Marion "Salvage" | brand-equivalency-guide + procedure-ch5-exceptions |
| F4 | A customer presents a title from a state not in Marion's recognized-state list. What does the examiner do? | Supervisor referral; unrecognized origin state | admin-rule-4-recognized-states + procedure-ch5-exceptions |

---

## Document index (30 documents)

| # | Filename | Category | Answers (primary) | Distractor for |
|---|---|---|---|---|
| 1 | statute-title-transfer-301-305.md | Marion Statute | — (legal authority; secondary to procedural docs) | — |
| 2 | statute-brands-310-312.md | Marion Statute | — (brand authority; secondary to equivalency guide) | — |
| 3 | statute-tax-320-322.md | Marion Statute | — (tax authority; secondary to admin rule 7) | — |
| 4 | statute-vin-inspection-330.md | Marion Statute | — (VIN authority; secondary to procedure ch3) | — |
| 5 | admin-rule-2-1-transfer-procedures.md | Admin Rule | F2 (ELT lien context) | — |
| 6 | admin-rule-2-4-emissions.md | Admin Rule (current) | A7, B1, D2 | admin-rule-2-4-emissions-superseded |
| 7 | admin-rule-2-4-emissions-superseded.md | Admin Rule (superseded) | — (distractor only) | D2 — old 20-yr exemption must NOT be used |
| 8 | admin-rule-4-recognized-states.md | Admin Rule | F4 | — |
| 9 | admin-rule-5-exceptions.md | Admin Rule | F1, F2, F3, F4 | — |
| 10 | admin-rule-7-tax.md | Admin Rule | A3, A4 | — |
| 11 | admin-rule-9-fee-schedule.md | Admin Rule (current) | A9, A10, D1 | admin-rule-9-fee-schedule-superseded |
| 12 | admin-rule-9-fee-schedule-superseded.md | Admin Rule (superseded) | — (distractor only) | D1 — old 4.5 % rate must NOT be used |
| 13 | procedure-ch1-overview.md | Procedure Manual | — (classification guidance; secondary) | — |
| 14 | procedure-ch3-vin-inspection.md | Procedure Manual | A6 | — |
| 15 | procedure-ch4-1-purchase-paper-no-lien.md | Procedure Manual | A1 | A2 (Verdana scenario looks adjacent) |
| 16 | procedure-ch4-2-purchase-paper-lien-released.md | Procedure Manual | — (supports lien-released scenarios) | F1 (active lien should NOT go here) |
| 17 | procedure-ch4-3-relocation.md | Procedure Manual | A8, B4 | A1 (relocation vs purchase confusion) |
| 18 | procedure-ch4-4-elt-conversion.md | Procedure Manual | A2 | A1 (ELT vs paper confusion) |
| 19 | procedure-ch5-exceptions.md | Procedure Manual | F1, F2, F3, F4 | procedure-ch4-2 (lien-released vs active) |
| 20 | procedure-ch6-7-fees-forms.md | Procedure Manual | A8 (form catalog), A9, A10 | — |
| 21 | brand-equivalency-guide.md | Reference | B2, B3, B5, F3 | — |
| 22 | tax-reciprocity-schedule.md | Reference | A4, A5, B6 | — |
| 23 | origin-state-verdana.md | Origin State Profile | A2 (ELT confirmation) | B1, B3, B4 |
| 24 | origin-state-crestwood.md | Origin State Profile | — (supports A1 context) | B5, B6 |
| 25 | origin-state-halloway.md | Origin State Profile | — (context only) | B1, B2 |
| 26 | origin-state-pembrook.md | Origin State Profile | — (context only) | B5, B6 |
| 27 | form-tr1-instructions.md | Form Instructions | A1, A8 | — |
| 28 | form-tr2-instructions.md | Form Instructions | A6 | — |
| 29 | form-tr3-instructions.md | Form Instructions | — (lien release scenarios) | — |
| 30 | form-tr10-instructions.md | Form Instructions | F1, F2, F3, F4 | — |

---

## Detailed entries

Each entry specifies the document's required content in enough detail to write it
correctly on the first pass. "Messiness" instructions create realistic formatting
so RAG can't rely on clean prose alone.

---

### 1. `statute-title-transfer-301-305.md`
**Category:** Marion Vehicle Code, §§ 301–305
**Purpose:** Legislative authority for out-of-state title transfers; cross-references brands (§ 310) and tax (§ 320). Secondary source — examiners cite the statute but work from administrative rules and the procedure manual.

**Must contain:**
- § 301: Jurisdiction — Marion requires titling of any motor vehicle operated on Marion roads.
- § 302: Out-of-state transfer — owner must surrender origin title within 30 days of establishing Marion residency or completing purchase.
- § 303: Required instruments — (a) origin title or ELT release confirmation; (b) completed Marion Form TR-1; (c) proof of insurance; (d) payment of applicable fees and tax. Lists are non-exhaustive ("including but not limited to").
- § 304: Liens — Marion title not issued while a lien is recorded on origin title unless a full release is provided. Delegates ELT lien release procedure to the Administrator.
- § 305: Brands — Marion title shall carry any brand shown on the origin title. Origin brand names not matching Marion nomenclature shall be converted per the Administrator's equivalency schedule (cross-reference § 310). Vehicles with branded titles processed pursuant to § 310 only.
- Running footer on each "page": `Marion Vehicle Code | Chapter 3 — Title Transfers | Page X of 4`
- Legal preamble: "Be it enacted by the General Assembly of the State of Marion, in session assembled..."
- Internal cross-reference in § 304 to "Admin. Rule 2.1, §§ 2.1.3–2.1.4 (ELT procedures)" without quoting those sections.

**Eval coverage:** Secondary/authority citation for A1, A2, F1, F2, F3; does not directly answer any question.
**Distractor for:** None.
**Messiness:** Legal boilerplate preamble; section numbers in inconsistent format (§ 303 vs Sec. 304); footer cuts off mid-sentence on "page 2"; internal cross-reference without content.

---

### 2. `statute-brands-310-312.md`
**Category:** Marion Vehicle Code, §§ 310–312
**Purpose:** Brand definitions and delegation of equivalency authority to the Administrator.

**Must contain:**
- § 310: Brand definitions — Salvage (vehicle declared total loss); Reconstructed (salvage rebuilt to roadworthy); Rebuilt (rebuilt per manufacturer spec, not from salvage); Flood Damage (water immersion); Odometer Rollback (odometer altered); Lemon Law Buyback (returned under lemon law statute).
- § 311: Duty to carry — brands are permanent and must appear on every subsequent title issued in Marion.
- § 312: Equivalency — Administrator shall publish a Brand Equivalency Schedule mapping origin-state brand names to Marion nomenclature. Vehicles branded under origin-state terminology are subject to the same supervisor review requirements as vehicles branded under Marion terminology. Equivalency schedule is binding and updated annually.
- Footer: `Marion Vehicle Code | Chapter 3 — Title Transfers | Page X of 3`
- Cross-reference from § 312 to "Admin. Rule 2.5 (Brand Equivalency Schedule)" — this is the admin rule number for the brand equivalency guide in regulatory numbering, though the document is published separately.

**Eval coverage:** Authority for B2, B3, B5, F3; secondary to brand-equivalency-guide.md for direct answers.
**Distractor for:** None.
**Messiness:** § 310 definitions formatted as a numbered list but using inconsistent punctuation (some entries end in periods, some in semicolons); § 312 cross-references a rule number ("Admin. Rule 2.5") while the equivalency guide uses a different heading.

---

### 3. `statute-tax-320-322.md`
**Category:** Marion Vehicle Code, §§ 320–322
**Purpose:** Legislative authority for sales/use tax on vehicle transfers; authorizes reciprocity agreements.

**Must contain:**
- § 320: Sales tax — transfer of a motor vehicle previously titled outside Marion is subject to Marion sales and use tax on the fair market value or declared purchase price, whichever is greater. Rate set by Admin Rule 7.
- § 321: Relocation — vehicles brought to Marion by a relocating owner are subject to use tax on NADA clean retail value. Owner must provide proof of residency established within 90 days.
- § 322: Reciprocity — the Director of Revenue may enter reciprocity agreements with other states. Where such an agreement exists, credit shall be given for sales or use tax paid to the origin state, not to exceed the Marion tax owed. No refund issued if credit exceeds Marion tax owed.
- Footer: `Marion Vehicle Code | Chapter 3 — Title Transfers | Page X of 2`

**Eval coverage:** Authority for A3, A4, A5; secondary to admin-rule-7-tax.md and tax-reciprocity-schedule.md.
**Distractor for:** None.
**Messiness:** § 322 refers to "the Director of Revenue" while admin-rule-7 refers to "the Tax Administrator" (same office, different reference).

---

### 4. `statute-vin-inspection-330.md`
**Category:** Marion Vehicle Code, § 330
**Purpose:** Statutory authority for mandatory VIN verification on out-of-state transfers.

**Must contain:**
- § 330(a): VIN verification required on all motor vehicles presented for out-of-state title transfer.
- § 330(b): Inspection must be performed by a Marion-licensed VIN inspector or law enforcement officer.
- § 330(c): Results recorded on Form TR-2 (VIN Inspection Certification), which must accompany the TR-1 application.
- § 330(d): VIN on vehicle must match VIN on origin title exactly. Discrepancy triggers supervisor referral.
- Footer: `Marion Vehicle Code | Chapter 3 — Title Transfers | Page 1 of 1`

**Eval coverage:** Authority for A6; secondary to procedure-ch3-vin-inspection.md.
**Distractor for:** None.
**Messiness:** Single-page document; § 330 subdivided as (a)–(d) but lettering resets mid-section; cross-reference to "Form TR-2" without explaining what it is.

---

### 5. `admin-rule-2-1-transfer-procedures.md`
**Category:** Marion Administrative Rule 2.1
**Purpose:** Step-by-step transfer procedures for paper and ELT title transfers; primary reference for ELT-specific process.

**Must contain:**
- § 2.1.1: Scope — applies to all out-of-state title transfers.
- § 2.1.2: Paper title transfers — examiner receives origin title, verifies: (a) title is in customer's name or properly assigned to customer; (b) all prior assignments completed in ink; (c) odometer disclosure present; (d) any brands noted. If lien stamp present, verify release endorsement or obtain Form TR-3.
- § 2.1.3: ELT (Electronic Lien and Title) — certain states do not issue paper titles; they maintain a central electronic title record. Verdana, and other states listed in Admin Rule 4, participate in ELT. For ELT-origin vehicles: (a) examiner cannot hold a paper title because none exists; (b) customer presents ELT Release Confirmation printed from origin state's portal; (c) if active lien shown in origin ELT record, lienholder must submit electronic release before Marion issues title; (d) Marion requests ELT conversion upon application approval.
- § 2.1.4: ELT with active lien — supervisor referral required per Admin Rule 5.2. Do not accept customer's statement that lien is paid; require ELT release record.
- § 2.1.5: Odometer disclosure — required for vehicles ≤ 10 model years old. Older vehicles: odometer disclosure box on TR-1 marked "EXEMPT."
- Cross-reference to Admin Rule 5 for exception procedures.
- Footer: `Marion Admin. Rule 2.1 — Transfer Procedures | Effective 2019-07-01 | Page X of 3`

**Eval coverage:** F2 (ELT active lien context), A2 (step-by-step ELT).
**Distractor for:** None.
**Messiness:** § 2.1.3 parenthetical "(a)–(d)" uses letters while the rest of the rule uses numbers; Verdana mentioned as an example of an ELT state but phrased as "such as Verdana" (hedged), which could confuse a retriever looking for definitive ELT state lists.

---

### 6. `admin-rule-2-4-emissions.md`
**Category:** Marion Administrative Rule 2.4 (current, effective 2023-01-01)
**Purpose:** Emissions testing requirements for vehicles registered in Marion. CURRENT VERSION.

**Must contain:**
- Effective date prominently: "Effective January 1, 2023. Supersedes prior version dated July 1, 2018."
- § 2.4.1: Applicability — all motor vehicles registering in Marion, including out-of-state transfers.
- § 2.4.2: County classification —
  - Metro counties (emissions required): Marion County, Riverside County, Capital County.
  - All other Marion counties: rural (emissions exempt).
- § 2.4.3: Age exemption — vehicles with a model year **25 or more years prior to the calendar year of registration** are exempt from emissions testing regardless of county.
  - Example: In calendar year 2026, vehicles with model year 2001 or earlier are exempt.
- § 2.4.4: Additional exemptions — new vehicles (current model year or one prior), electric and hydrogen fuel cell vehicles, motorcycles under 50cc.
- § 2.4.5: Proof — customer presents emissions certificate issued by a Marion-authorized station within 90 days of application. See Admin Rule 3 for authorized stations.
- § 2.4.6: Out-of-state emissions programs — origin state emissions testing does NOT satisfy Marion's requirement. Marion test required regardless of any test performed in the origin state.
- Footer: `Marion Admin. Rule 2.4 — Emissions Testing | Effective 2023-01-01 | CURRENT VERSION | Page X of 2`

**Eval coverage:** A7 (1998 model — exempt under 25yr rule), B1 (Halloway no-emissions — Marion rules still apply), D2 (current 25yr exemption).
**Distractor for:** admin-rule-2-4-emissions-superseded (never cite the 20yr exemption).
**Messiness:** § 2.4.3 example embeds the year "2026" (will age, but that's intentional messiness); § 2.4.6 is clearly labeled but positioned after the exemption list, so a naive chunker may separate it from the county/age rules.

---

### 7. `admin-rule-2-4-emissions-superseded.md`
**Category:** Marion Administrative Rule 2.4 (superseded, effective 2018-07-01, replaced 2023-01-01)
**Purpose:** Old emissions rule — distractor only. Must contain the WRONG (old) exemption age so retrieval tests can detect contamination.

**Must contain:**
- Header: "SUPERSEDED. Replaced by Admin. Rule 2.4 effective January 1, 2023. Retained for reference only."
- § 2.4.1–2.4.2: Same county classification as current version (metro/rural split is unchanged).
- § 2.4.3 (superseded): Age exemption — vehicles with a model year **20 or more years prior** to calendar year of registration are exempt. _(This is the WRONG answer for D2.)_
- § 2.4.4 (superseded): Same additional exemptions.
- § 2.4.5 (superseded): Same proof requirements.
- § 2.4.6: NOTE — nothing in this rule addresses origin-state programs. Rule is silent on Halloway; examiner must apply Marion's requirement.
- Footer: `Marion Admin. Rule 2.4 — Emissions Testing | Effective 2018-07-01 | SUPERSEDED 2023-01-01 | Page X of 2`

**Eval coverage:** NONE — this document must never be the correct answer to any question.
**Distractor for:** D2 — the 20yr exemption is the wrong answer. System must prefer the current version.
**Messiness:** Header supersession notice placed in a gray "administrative note" box that a naive chunker might skip; footer repeats supersession date but uses a different format than the header.

---

### 8. `admin-rule-4-recognized-states.md`
**Category:** Marion Administrative Rule 4
**Purpose:** List of states whose out-of-state titles Marion recognizes. Vehicles from unlisted states require supervisor referral. Primary source for F4.

**Must contain:**
- § 4.1: Marion accepts out-of-state titles from the following states. Any state not on this list requires supervisor referral per Admin Rule 5.2(d).
- § 4.2: Recognized states list — formatted as a table with columns: State name | Title form (Paper / ELT / Mixed) | Notes.
  - Verdana | ELT | Electronic title; no paper title issued. ELT release confirmation required.
  - Crestwood | Paper | Physical title. Lien release by endorsement or separate Form CST-LR.
  - Halloway | Paper | Physical title. Note: Halloway does not operate an emissions testing program; Marion requirements apply.
  - Pembrook | Mixed | Paper or ELT depending on lienholder participation. No reciprocity agreement for sales tax.
  - Include 6–8 additional fictional states (Alderton, Brexham, Corville, Dunmore, Elsworth, Fallkirk) with "Paper" or "Mixed" and minimal notes — for realism and to make the list look like a real roster, not a contrived 4-state list.
- § 4.3: Effective date — list updated annually. Current version effective 2025-01-01.
- § 4.4: ELT states — states designated "ELT" in § 4.2 do not issue paper titles. Examiners must not require a paper title from customers titling vehicles from ELT states.
- Footer: `Marion Admin. Rule 4 — Recognized States | Effective 2025-01-01 | Page X of 2`

**Eval coverage:** F4 (unrecognized state → supervisor).
**Distractor for:** None.
**Messiness:** Additional fictional states listed with minimal notes, creating realistic noise; § 4.2 table has a merged note cell for Verdana that runs over two visual rows (simulating a Word-generated table exported to text).

---

### 9. `admin-rule-5-exceptions.md`
**Category:** Marion Administrative Rule 5
**Purpose:** Supervisor referral triggers and TR-10 completion. Primary procedural source for all Group F questions.

**Must contain:**
- § 5.1: General — certain title transfer conditions require supervisor review before the examiner provides the customer with a document checklist or fee total.
- § 5.2: Referral triggers (examiner MUST refer to supervisor):
  - (a) Active lien on record in origin state title system (paper or ELT).
  - (b) Any brand appearing on origin title, regardless of whether Marion's equivalency schedule covers it.
  - (c) Origin title cannot be produced or confirmed (missing title, customer claims title lost, ELT record not accessible).
  - (d) Origin state not listed in Admin Rule 4 (Recognized States).
  - (e) VIN discrepancy — VIN on vehicle does not match VIN on origin title.
- § 5.3: Examiner conduct during referral — examiner SHALL NOT tell the customer what documents are required or what fee is owed until supervisor has reviewed and cleared the exception. Examiner may provide the customer with a conditional list clearly marked "SUBJECT TO SUPERVISOR REVIEW — NOT FINAL."
- § 5.4: Completing Form TR-10:
  - Block 1: Customer name, contact, date.
  - Block 2: Vehicle VIN, year, make, model.
  - Block 3: Trigger — check all applicable boxes from § 5.2.
  - Block 4: What examiner has already verified (title form, lien status if determinable, brand if shown).
  - Block 5: Supervisor disposition — leave blank; supervisor completes.
- § 5.5: Turnaround — supervisor must respond within 2 business days. Customer may be asked to return.
- § 5.6: Resolution codes (supervisor fills):
  - CLEARED: Exception resolved; examiner may proceed.
  - PEND-DOCS: Additional documentation required from customer.
  - DENIED: Transfer cannot proceed; reason stated.
- Footer: `Marion Admin. Rule 5 — Exception Procedures | Effective 2021-03-15 | Page X of 3`

**Eval coverage:** F1, F2, F3, F4.
**Distractor for:** procedure-ch4-2 (lien-released path is NOT an exception; active lien IS).
**Messiness:** § 5.2 trigger list uses "(a)–(e)" while § 5.4 TR-10 blocks use numbered "Block 1–5"; § 5.3 includes the phrase "conditional list clearly marked 'SUBJECT TO SUPERVISOR REVIEW'" — this is the authority for the system's conditionalNote field, but it's buried in a single paragraph.

---

### 10. `admin-rule-7-tax.md`
**Category:** Marion Administrative Rule 7
**Purpose:** Tax computation formula, taxable value determination, reciprocity credit. Primary source for A3, A4.

**Must contain:**
- § 7.1: Tax rate — Marion imposes a 5.5% sales and use tax on all out-of-state vehicle transfers, effective January 1, 2023.
- § 7.2: Taxable value —
  - PURCHASE: taxable value = the greater of (a) declared purchase price as shown on bill of sale, or (b) NADA Clean Retail value as of transfer date. If declared price is within 80% of NADA, examiner uses declared price; if declared price is below 80% of NADA, examiner uses NADA value.
  - RELOCATION: taxable value = NADA Clean Retail value. No bill of sale required for tax purposes.
- § 7.3: Reciprocity credit —
  - If origin state has a valid reciprocity agreement with Marion (see Tax Reciprocity Schedule), the customer receives a credit equal to the lesser of: (a) sales or use tax verifiably paid to the origin state, or (b) the Marion tax owed on the same taxable value.
  - If reciprocity credit equals or exceeds Marion tax owed, additional tax due = $0. Marion does not refund the excess.
  - If origin state has NO reciprocity agreement with Marion, no credit applies. Full Marion tax owed.
  - Worked example: Taxable value $15,000; Crestwood rate 6.0%; tax paid to Crestwood = $900. Marion rate 5.5%; Marion tax = $825. Credit = min($900, $825) = $825. Additional due = $825 − $825 = $0.
  - Worked example: Taxable value $20,000; Pembrook (no reciprocity). Marion tax = $20,000 × 5.5% = $1,100. Credit = $0. Additional due = $1,100.
- § 7.4: Payment — tax collected at time of application. Accepted methods: certified check, money order, debit card. Personal checks not accepted.
- Footer: `Marion Admin. Rule 7 — Vehicle Transfer Tax | Effective 2023-01-01 | Page X of 2`

**Eval coverage:** A3 (rate + basis), A4 (Crestwood reciprocity calculation).
**Distractor for:** None.
**Messiness:** Two worked examples embedded in § 7.3 use specific dollar amounts (helpful for evals); § 7.4 payment methods are a realistic non-sequitur that a chunker may include with the tax formula.

---

### 11. `admin-rule-9-fee-schedule.md`
**Category:** Marion Administrative Rule 9 — Fee Schedule (current, effective 2023-01-01)
**Purpose:** Current fee schedule. CURRENT VERSION. Primary source for A9, A10, D1.

**Must contain:**
- Header: "CURRENT FEE SCHEDULE. Effective January 1, 2023. Supersedes prior schedule dated July 1, 2018."
- Table of fees:
  | Fee | Amount |
  |---|---|
  | Out-of-state title transfer application (Form TR-1) | $25.00 |
  | VIN inspection fee (Form TR-2) | $15.00 |
  | Emissions test (paid to testing station, not DMV; listed for reference) | $35.00 |
  | Base registration fee — passenger vehicle, ≤ 5,000 lbs | $45.00 |
  | Base registration fee — passenger vehicle, 5,001–8,500 lbs | $65.00 |
  | Lien release processing (Form TR-3) | $5.00 |
  | Supervisor exception referral processing (Form TR-10) | No fee |
  | Late penalty | 10% of total owed per month, capped at 50% |
- Note: Emissions test fee paid directly to authorized testing station; DMV collects title and registration fees only.
- Note: Registration fee is in addition to title transfer fee; both due at application.
- Footer: `Marion Admin. Rule 9 — Fee Schedule | Effective 2023-01-01 | CURRENT VERSION | Page 1 of 1`

**Eval coverage:** A9 ($25.00 title fee), A10 ($15.00 VIN fee), D1 (current rate context).
**Distractor for:** admin-rule-9-fee-schedule-superseded (must not return old rates).
**Messiness:** Emissions fee listed with a note that it's not collected by DMV — creates ambiguity about what "DMV fee" means; late penalty expressed as a rate rather than a flat amount.

---

### 12. `admin-rule-9-fee-schedule-superseded.md`
**Category:** Marion Administrative Rule 9 — Fee Schedule (superseded, effective 2018-07-01)
**Purpose:** Old fee schedule — distractor only. Contains OLD (wrong) sales tax rate and VIN fee.

**Must contain:**
- Header: "SUPERSEDED. Replaced by Admin. Rule 9 effective January 1, 2023. Retained for reference only."
- Table of fees (OLD — do not use):
  | Fee | Amount |
  |---|---|
  | Out-of-state title transfer application (Form TR-1) | $20.00 _(old, now $25.00)_ |
  | VIN inspection fee (Form TR-2) | $10.00 _(old, now $15.00)_ |
  | Base registration fee — passenger vehicle, ≤ 5,000 lbs | $38.00 _(old, now $45.00)_ |
  | Late penalty | 10% per month, capped at 40% _(old cap; now 50%)_ |
- Note: Tax rate applicable to this schedule was 4.5%. _(OLD — current rate is 5.5%.)_
- Footer: `Marion Admin. Rule 9 — Fee Schedule | Effective 2018-07-01 | SUPERSEDED 2023-01-01 | Page 1 of 1`

**Eval coverage:** NONE — must never be the correct answer.
**Distractor for:** D1 (old $20 title fee, old 4.5% tax rate must NOT be used), A9, A10.
**Messiness:** Supersession notice in same font as the rest of the document (easy for a retriever to miss); tax rate noted in a footer line rather than a prominent field.

---

### 13. `procedure-ch1-overview.md`
**Category:** Examiner Procedure Manual, Chapter 1
**Purpose:** Overview of the transfer workflow; classification of scenarios; customer interview guide.

**Must contain:**
- 1.1 Purpose: This manual governs the conduct of Marion DMV examiners processing out-of-state title transfers.
- 1.2 Scenario classification (first step at the counter):
  - Ask: "Did you purchase this vehicle from a seller in another state, or did you bring it with you when you moved to Marion?"
  - PURCHASE: Vehicle sold by out-of-state seller to customer; requires bill of sale.
  - RELOCATION: Customer owned the vehicle before moving; no purchase in Marion.
  - If unclear: document customer's explanation verbatim; supervisor review.
- 1.3 Initial document review:
  - Paper title or ELT confirmation?
  - Lien release present?
  - Any brands on title face?
  - VIN match confirmed?
- 1.4 Flowchart (ASCII art):
  ```
  Customer arrives → Classify (Purchase / Relocation)
       ↓
  Origin title form? (Paper / ELT)
       ↓
  Lien status? (None / Released / Active)
       ↓           ↓
  Proceed       STOP → Supervisor (Admin Rule 5)
       ↓
  Brand? (Clean / Branded)
       ↓           ↓
  Proceed       STOP → Supervisor (Admin Rule 5)
       ↓
  Inspections (VIN — always; Emissions — by county/age)
       ↓
  Compute fees and tax
       ↓
  Issue checklist
  ```
- 1.5 Reference summary: checklists in Ch. 4; exceptions in Ch. 5; fees in Ch. 6; forms in Ch. 7.
- Footer: `Examiner Procedure Manual | Chapter 1 — Overview | Rev. 2024-03 | Page X of 4`

**Eval coverage:** Secondary/orientation for all questions; does not directly answer any eval question.
**Distractor for:** None.
**Messiness:** ASCII flowchart will chunk poorly; § 1.4 is a visual aid with no prose equivalent.

---

### 14. `procedure-ch3-vin-inspection.md`
**Category:** Examiner Procedure Manual, Chapter 3
**Purpose:** VIN verification steps, authorized inspectors, TR-2 completion. Primary source for A6.

**Must contain:**
- 3.1 Applicability: VIN inspection required for ALL out-of-state title transfers. No exceptions.
- 3.2 Who may inspect: (a) Marion-licensed motor vehicle inspector; (b) active Marion law enforcement officer; (c) licensed vehicle dealer (Marion license). Inspections by origin-state officials not accepted.
- 3.3 What is inspected: Physical VIN plate on dashboard (driver's side); VIN sticker on door jamb; VIN on engine block (where accessible). All three must match origin title VIN.
- 3.4 Completing Form TR-2:
  - Inspector prints name, license number, date.
  - Records each VIN location examined.
  - Signs and dates.
  - Customer brings completed TR-2 to DMV with the title application.
- 3.5 VIN discrepancy: If any VIN location does not match title, examiner stops processing. Refers to supervisor (Admin Rule 5.2(e)). Do not advise customer.
- 3.6 Timing: TR-2 must be dated within 90 days of the title application.
- Footer: `Examiner Procedure Manual | Chapter 3 — VIN Inspection | Rev. 2024-03 | Page X of 2`

**Eval coverage:** A6 (VIN inspection requirement and form).
**Distractor for:** None.
**Messiness:** § 3.2 lists inspector types as "(a)–(c)" while § 3.4 TR-2 instructions are unnumbered; § 3.6 timing requirement placed at end, separated from § 3.4 by the discrepancy section.

---

### 15. `procedure-ch4-1-purchase-paper-no-lien.md`
**Category:** Examiner Procedure Manual, Chapter 4 § 4.1
**Purpose:** Document checklist for the base happy path: PURCHASE + PAPER TITLE + NO LIEN. Primary source for A1.

**Must contain:**
- Scenario header: Transfer Type: PURCHASE | Title Form: PAPER | Lien: NONE | Brand: CLEAN
- 4.1.1 Required documents — customer must produce ALL of the following:
  1. Original origin title, assigned/signed over to buyer. All prior assignments complete.
  2. Bill of sale or purchase agreement showing vehicle description (year/make/model/VIN), purchase price, buyer and seller names and addresses, date of sale.
  3. Completed Marion Form TR-1 (Out-of-State Title Transfer Application).
  4. Completed Marion Form TR-2 (VIN Inspection Certification) — must be dated within 90 days.
  5. Proof of Marion insurance (policy declaration page or insurance card).
  6. Odometer disclosure (included in TR-1 or as a separate OD form for vehicles ≤ 10 model years old).
  7. Payment: title transfer fee ($25.00) + registration fee ($45.00 base) + sales tax owed.
- 4.1.2 Notes:
  - If vehicle is registered in Marion County, Riverside County, or Capital County AND model year is less than 25 years old: also require Marion emissions certificate (Form EMIT-1).
  - No lien release needed — confirm title face shows no lien stamp. If any lien mark visible, refer to Ch. 5.
- 4.1.3 Examiner checklist (box-check format): one checkbox per item above.
- Footer: `Examiner Procedure Manual | Chapter 4 — Checklists | §4.1 Purchase/Paper/No Lien | Rev. 2024-03`

**Eval coverage:** A1 (complete checklist for this scenario).
**Distractor for:** A2 (ELT path is different — no paper title); F1 (active lien should go to Ch. 5, not here).
**Messiness:** Checklist is a box-check list that will chunk as a list of short items; § 4.1.2 notes are after the main list, easy to miss if retrieval truncates; emissions note is embedded in the notes rather than the main checklist.

---

### 16. `procedure-ch4-2-purchase-paper-lien-released.md`
**Category:** Examiner Procedure Manual, Chapter 4 § 4.2
**Purpose:** Checklist for PURCHASE + PAPER TITLE + RELEASED LIEN.

**Must contain:**
- Scenario header: Transfer Type: PURCHASE | Title Form: PAPER | Lien: RELEASED | Brand: CLEAN
- 4.2.1 Required documents:
  1. All items from § 4.1.1 (1–7).
  2. PLUS one of the following to establish lien release:
     - (a) Lien release endorsement on title face, signed by lienholder and notarized; OR
     - (b) Separate lien release letter on lienholder letterhead, referencing the VIN, signed by an authorized officer; OR
     - (c) Completed Marion Form TR-3 (Lien Release Acknowledgment) if lienholder is registered with Marion DMV.
- 4.2.2 Notes:
  - Examiner verifies lienholder name on release matches lienholder name on title.
  - If any doubt about release authenticity, refer to supervisor.
  - This section covers RELEASED liens. Active (unreleased) liens require supervisor referral — see Ch. 5.
- Footer: `Examiner Procedure Manual | Chapter 4 — Checklists | §4.2 Purchase/Paper/Released Lien | Rev. 2024-03`

**Eval coverage:** None directly (no eval question targets this specific scenario). Supports lien-released scenarios.
**Distractor for:** F1 (active lien MUST NOT be processed under this section — the note in 4.2.2 is the only guard).
**Messiness:** § 4.2.1 references "All items from § 4.1.1 (1–7)" without repeating them — retrieval must follow the cross-reference; lien release options (a)–(c) formatted inconsistently (bold label on (a), no bold on (b) and (c)).

---

### 17. `procedure-ch4-3-relocation.md`
**Category:** Examiner Procedure Manual, Chapter 4 § 4.3
**Purpose:** Checklist for RELOCATION transfers. Primary source for A8, B4.

**Must contain:**
- Scenario header: Transfer Type: RELOCATION | Title Form: PAPER or ELT | Lien: NONE (lien at relocation uncommon; if lien exists, see Ch. 5)
- 4.3.1 Required documents:
  1. Origin title (paper) OR ELT Release Confirmation (if ELT state).
  2. Proof of Marion residency — any two of: (a) signed lease or mortgage statement in customer's name at Marion address; (b) Marion utility bill dated within 60 days; (c) Marion employer pay stub with Marion address. Must establish residency within 90 days of title application.
  3. Completed Marion Form TR-1. In the "Transfer Type" field, select RELOCATION; leave "Purchase Price" blank.
  4. Completed Marion Form TR-2 (VIN inspection), dated within 90 days.
  5. Proof of Marion insurance.
  6. Payment: title fee ($25.00) + registration fee ($45.00) + use tax owed (computed on NADA clean retail, not purchase price).
- 4.3.2 Tax note: For relocation transfers, taxable value is NADA Clean Retail as of transfer date. Customer should bring NADA printout or examiner looks up value. Reciprocity credit applies if origin state has agreement with Marion.
- 4.3.3 Emissions: Same county/age rule as purchase transfers applies.
- 4.3.4 Notes: Proof of residency must predate or be contemporaneous with application. If residency proof is older than 90 days before application, supervisor review.
- Footer: `Examiner Procedure Manual | Chapter 4 — Checklists | §4.3 Relocation | Rev. 2024-03`

**Eval coverage:** A8 (relocation forms), B4 (what tax docs to bring for Verdana relocation).
**Distractor for:** A1 (relocation vs. purchase has different forms and tax basis).
**Messiness:** § 4.3.2 tax note has "NADA clean retail" — capitalization varies from other documents (some say "NADA Clean Retail," some say "NADA clean retail value"); residency proof list uses (a)–(c) sub-lettering under item 2.

---

### 18. `procedure-ch4-4-elt-conversion.md`
**Category:** Examiner Procedure Manual, Chapter 4 § 4.4
**Purpose:** Checklist and process for ELT-origin title transfers. Primary source for A2.

**Must contain:**
- Scenario header: Transfer Type: PURCHASE or RELOCATION | Title Form: ELT | Lien: NONE (active ELT lien → Ch. 5)
- 4.4.1 What ELT means: Certain states (including Verdana — see Admin Rule 4 for full list) do not print paper titles. The title record is held electronically in the origin state's system. Marion recognizes ELT records from all states listed in Admin Rule 4.
- 4.4.2 What the customer brings instead of a paper title:
  - ELT Release Confirmation: a printed or emailed document from the origin state's DMV or ELT system confirming (a) the vehicle is titled in the customer's name, (b) any lien has been released, (c) the record has been flagged for transfer to Marion.
  - Customer must bring the printed ELT Release Confirmation. Marion cannot accept the customer's verbal confirmation alone.
- 4.4.3 Required documents (ELT transfer):
  1. ELT Release Confirmation (replaces paper title).
  2. Bill of sale (if PURCHASE) or proof of Marion residency (if RELOCATION).
  3. Completed Marion Form TR-1.
  4. Completed Marion Form TR-2 (VIN inspection).
  5. Proof of Marion insurance.
  6. Payment: title fee + registration fee + tax owed.
- 4.4.4 Marion's process: Upon receipt of a complete ELT application, Marion sends an electronic request to the origin state's DMV to transfer the title record. Origin state cancels the ELT record; Marion issues a Marion title. Typically completed within 5 business days.
- 4.4.5 ELT active lien: If ELT Release Confirmation shows an active lien, stop. Supervisor referral per Ch. 5. The lienholder must release the lien in the origin ELT system first. Customer must obtain a new ELT Release Confirmation showing the lien as released.
- Footer: `Examiner Procedure Manual | Chapter 4 — Checklists | §4.4 ELT Conversion | Rev. 2024-03`

**Eval coverage:** A2 (full ELT process for Verdana vehicle).
**Distractor for:** A1 (ELT ≠ paper; a paper-title checklist from § 4.1 is wrong for ELT-origin vehicles).
**Messiness:** § 4.4.4 describes Marion's internal process (not customer-facing) but sits in the same chapter as customer checklists; "5 business days" is a processing time (not answerable from corpus for C4-style questions).

---

### 19. `procedure-ch5-exceptions.md`
**Category:** Examiner Procedure Manual, Chapter 5
**Purpose:** Examiner-facing exception procedures. Procedural complement to Admin Rule 5. Primary source for all Group F.

**Must contain:**
- 5.1 When to refer: Refer to supervisor immediately when any condition listed in Admin Rule 5.2 is present. Do not continue checklist generation or fee computation.
- 5.2 Active lien — paper title:
  - Title shows lien stamp with no corresponding release. Example: "MIDWEST AUTO FINANCE, lien date 2022-04-01" with no release stamp.
  - Action: Tell customer "We need to review this with a supervisor; please wait." Complete Form TR-10 (Blocks 1–4). Do not tell customer the specific issue before supervisor reviews.
  - Conditional checklist: You may provide the customer with a list of documents they will likely need ONCE the exception is resolved, clearly marked "CONDITIONAL — SUBJECT TO SUPERVISOR REVIEW. DO NOT ACT ON THIS LIST UNTIL SUPERVISOR CLEARS YOUR TRANSACTION."
- 5.3 Active lien — ELT:
  - ELT Release Confirmation shows lien status as ACTIVE or OPEN.
  - Action: Same as 5.2. Note: ELT lien release requires the lienholder to submit an electronic release through the origin state's system. Customer cannot release an ELT lien at the Marion counter.
- 5.4 Branded titles:
  - Any brand appearing on origin title — recognized, equivalent, or unrecognized — requires supervisor referral.
  - Examiner identifies the brand name from origin title and looks it up in the Brand Equivalency Guide.
  - If found: record origin brand and Marion equivalent on TR-10 Block 4. Tell customer "The brand on this title requires additional review." Do not state what the brand means.
  - If not found: record origin brand as "UNRECOGNIZED — not in equivalency guide" on TR-10. Supervisor determines how to proceed.
  - Conditional checklist still provided with note.
- 5.5 Missing or unverifiable documentation:
  - Customer cannot produce origin title (claims lost, mailed to wrong address, etc.).
  - ELT Release Confirmation not available or unverifiable.
  - Supervisor referral. Do not attempt to process without origin documentation.
- 5.6 Unrecognized origin state:
  - Origin state not found in Admin Rule 4.
  - Supervisor referral. Supervisor contacts Marion DMV Legal for guidance.
  - Do not tell customer that their state is unrecognized; say "We need to verify some details with a supervisor."
- Footer: `Examiner Procedure Manual | Chapter 5 — Exception Handling | Rev. 2024-03 | Page X of 3`

**Eval coverage:** F1 (active lien, paper), F2 (active lien, ELT), F3 (branded title), F4 (unrecognized state).
**Distractor for:** procedure-ch4-2 (released lien does NOT go to Ch. 5; active lien DOES).
**Messiness:** § 5.2 contains an example with a real-sounding lienholder name "MIDWEST AUTO FINANCE" — which is exactly the lienholder in F1. The example makes the document more realistic but also means the F1 question specifically matches text in this document (good for retrieval). Conditional checklist instructions in § 5.2 and § 5.3 are nearly identical (duplication is intentional — realistic).

---

### 20. `procedure-ch6-7-fees-forms.md`
**Category:** Examiner Procedure Manual, Chapters 6–7
**Purpose:** Fee computation worksheet and form catalog. Secondary source for A8 (forms), A9–A10 (fees).

**Must contain:**
- Chapter 6 — Fee Worksheet:
  - 6.1: Step 1 — Title transfer fee: $25.00 (always).
  - 6.2: Step 2 — Registration fee: $45.00 (≤ 5,000 lbs) or $65.00 (5,001–8,500 lbs). Weight from VIN decode or title.
  - 6.3: Step 3 — VIN inspection fee: $15.00 (always — collected separately from TR-2 inspector or at DMV window depending on county).
  - 6.4: Step 4 — Emissions fee: $35.00 IF required (metro county + model year < 25 years). Paid to testing station, not DMV.
  - 6.5: Step 5 — Sales/use tax: see Admin Rule 7 computation. Reciprocity credit applied per Tax Reciprocity Schedule.
  - 6.6: Step 6 — Lien release processing: $5.00 if Form TR-3 used.
  - 6.7: Total: sum of Steps 1–6 (Step 4 is informational if paid to station).
  - Late penalty note: 10% per month if application submitted more than 30 days after purchase or residency established.
- Chapter 7 — Form Catalog:
  - TR-1: Out-of-State Title Transfer Application — required for all transfers.
  - TR-2: VIN Inspection Certification — required for all transfers; completed by inspector.
  - TR-3: Lien Release Acknowledgment — required if lien released via Marion DMV process (not all lien releases require TR-3; see Ch. 4).
  - TR-10: Supervisor Exception Referral — required when any Ch. 5 condition triggered.
  - EMIT-1: Emissions Certificate — issued by testing station; customer brings to DMV.
  - OD-1: Odometer Disclosure Statement — required for vehicles ≤ 10 model years old if not included in TR-1.
- Footer: `Examiner Procedure Manual | Chapters 6–7 — Fees & Forms | Rev. 2024-03 | Page X of 4`

**Eval coverage:** A8 (forms for relocation), A9 ($25.00 from Step 1), A10 ($15.00 from Step 3).
**Distractor for:** None.
**Messiness:** Chapter 6 and Chapter 7 combined in one document (realistic — often stapled together); Step 4 emissions fee noted as "paid to testing station" — may confuse "fees due to DMV" vs total out-of-pocket.

---

### 21. `brand-equivalency-guide.md`
**Category:** Brand Equivalency Guide (published under Admin Rule 2.5 authority)
**Purpose:** The canonical mapping table from origin-state brand terminology to Marion brand names. Primary source for B2, B3, B5, F3.

**Must contain:**
- Title: Marion DMV Brand Equivalency Schedule — Published pursuant to Marion Vehicle Code § 312 and Admin. Rule 2.5
- Effective date: January 1, 2025. Supersedes prior schedule dated January 1, 2022.
- Legal preamble: "The following schedule establishes the equivalency between brand designations used by other jurisdictions and the brand nomenclature of the State of Marion. Examiners shall apply the Marion brand shown in this schedule regardless of the terminology used on the origin title..."
- Full mapping table (use the canonical brand equivalency table from the Ground-truth numbers section above). Include all 4 origin states and all their brands.
- Critical rows to make unambiguous:
  - Halloway / Rebuilt / → Marion: Reconstructed — add note: "Note: Halloway's 'Rebuilt' designation indicates a vehicle rebuilt from a salvage condition. This is equivalent to Marion's 'Reconstructed' brand, not Marion's 'Rebuilt' brand. See §312 definitions."
  - Pembrook / Salvage Rebuilt / → Marion: Rebuilt — add note: "Note: Pembrook's 'Salvage Rebuilt' is a single compound designation indicating a vehicle that was declared salvage but has been rebuilt to roadworthy condition. This maps to Marion's 'Rebuilt' brand as a single brand, NOT as two separate brands (Salvage + Rebuilt)."
- Unrecognized brands section: "Any brand designation appearing on an origin title that is NOT listed in this schedule must be treated as UNRECOGNIZED. Refer to supervisor per Admin Rule 5.2(b). Do not attempt to assign a Marion equivalent."
- Table also includes generic brands applicable to all states: Theft Recovery (→ Marion: Theft Recovery), Grey Market Import (→ Marion: UNRECOGNIZED — supervisor only).
- Footer: `Marion DMV Brand Equivalency Schedule | Effective 2025-01-01 | Page X of 3`

**Eval coverage:** B2 (Verdana "Rebuilt" → Marion "Rebuilt"), B3 (Halloway "Rebuilt" → Marion "Reconstructed"), B5 (Pembrook "Salvage Rebuilt" → Marion "Rebuilt", single brand), F3 (Halloway "Junk" → Marion "Salvage").
**Distractor for:** None — this document IS the correct answer for brand mapping questions. Origin state profiles are the distractors relative to this document.
**Messiness:** Legal preamble and effective date take up the first two "chunks" before the actual table appears; generic brands at the bottom of the table (Theft Recovery, Grey Market Import) add noise; notes on Halloway and Pembrook are critical but styled as small-print footnotes below the main table rows.

---

### 22. `tax-reciprocity-schedule.md`
**Category:** Tax Reciprocity Schedule (published under Marion Vehicle Code § 322)
**Purpose:** Definitive reciprocity agreement list with origin-state rates and worked examples. Primary source for A4, A5, B6.

**Must contain:**
- Title: Marion Tax Reciprocity Schedule — Vehicle Sales and Use Tax
- Effective date: January 1, 2025. Issued by Marion Department of Revenue pursuant to Marion Vehicle Code § 322.
- Introduction: "Marion has entered reciprocity agreements with the following states. For vehicles transferred from a listed state, the customer receives credit for sales or use tax paid to that state, not to exceed the Marion tax owed. For vehicles from states NOT listed, no credit applies."
- Reciprocity table:
  | Origin State | Agreement | Origin Rate | Marion Tax (5.5%) on $20,000 Example | Credit | Additional Due |
  |---|---|---|---|---|---|
  | Verdana | YES | 5.0% | $1,100 | min($1,000, $1,100) = $1,000 | $100 |
  | Crestwood | YES | 6.0% | $1,100 | min($1,200, $1,100) = $1,100 | $0 |
  | Halloway | YES | 4.5% | $1,100 | min($900, $1,100) = $900 | $200 |
  | Pembrook | **NO** | N/A | $1,100 | **$0** | **$1,100** |
  - Pembrook row note (in bold): "**PEMBROOK: NO RECIPROCITY AGREEMENT. No credit for tax paid in Pembrook, regardless of amount paid. Full Marion tax owed.**"
- Additional worked example for A4: "Example — Crestwood, $15,000 vehicle: Marion tax = $15,000 × 5.5% = $825. Tax paid in Crestwood = $15,000 × 6.0% = $900. Credit = min($900, $825) = $825. Additional due = $825 − $825 = $0."
- General states not listed note: "States not appearing in this table have NO reciprocity agreement with Marion. Full Marion tax applies."
- Footer: `Marion Tax Reciprocity Schedule | Effective 2025-01-01 | Marion Dept. of Revenue | Page 1 of 1`

**Eval coverage:** A4 (Crestwood $15k worked example → $0 additional), A5 (Pembrook = no reciprocity), B6 (Pembrook credit = $0).
**Distractor for:** None.
**Messiness:** Table columns use "$20,000 Example" (not the $15,000 in A4) — forces the system to compute, not just read; Pembrook row uses N/A for rate; worked example for A4 is below the main table, not adjacent to the Crestwood row.

---

### 23. `origin-state-verdana.md`
**Category:** Origin State Profile — Verdana
**Purpose:** Reference profile for Verdana. Confirms ELT status. Contains "Rebuilt" brand (mapped to Marion "Rebuilt"). DISTRACTOR for B1, B3, B4.

**Must contain:**
- Header: Marion DMV — Origin State Reference Profile: VERDANA
- Title system: ELT (Electronic Lien and Title). Verdana eliminated paper titles for passenger vehicles effective 2015. Customers will not have a paper title; they must obtain an ELT Release Confirmation from the Verdana DMV portal (portal.verdana.gov/elt-release).
- Lien procedures: Active liens appear in the Verdana ELT system. Lienholder must submit electronic release through Verdana's secure lender portal. Release typically processes within 3 business days.
- Emissions program: Verdana operates an emissions testing program. Verdana emissions test is NOT accepted in Marion; Marion test required. (← this sentence is critical for B1 — Verdana HAS an emissions program, unlike Halloway, but Marion still requires its own test.)
- Brand terminology:
  | Verdana Brand | Marion Equivalent |
  |---|---|
  | Salvage | Salvage |
  | Rebuilt | Rebuilt _(Verdana's "Rebuilt" = vehicle rebuilt per manufacturer spec, not from salvage — maps to Marion "Rebuilt," NOT "Reconstructed")_ |
  | Flood Damage | Flood Damage |
  | Odometer Fraud | Odometer Rollback |
  | Lemon Law Buyback | Lemon Law Buyback |
- Note: "Verdana's 'Rebuilt' designation differs from Halloway's 'Rebuilt' — see Brand Equivalency Guide for full equivalency table."
- Tax: Verdana rate 5.0%. Marion has a reciprocity agreement with Verdana.
- Contact: Verdana DMV main office contact not provided. For ELT release portal issues, contact Verdana DMV at [PORTAL HELPDESK — CONTACT DETAILS REDACTED — NOT FOR EXAMINER USE].
- Footer: `Marion DMV Origin State Profile — Verdana | Rev. 2024-06 | Page 1 of 2`

**Eval coverage:** A2 (confirms Verdana is ELT — needed alongside procedure-ch4-4).
**Distractor for:** B1 (Verdana HAS an emissions program — but that doesn't answer whether Marion requires a test), B3 (contains "Rebuilt" → "Rebuilt" mapping, not the Halloway mapping), B4 (Verdana's ELT procedure information is adjacent to tax/relocation questions but doesn't answer the Marion tax procedure question).
**Messiness:** Contact details redacted (realistic — Marion examiners don't call Verdana); "Rebuilt" note cross-references Brand Equivalency Guide (good — system should follow that reference); Verdana tax rate (5.0%) appears in the profile, which might confuse a system trying to answer A4 (it's computing Crestwood reciprocity, not Verdana).

---

### 24. `origin-state-crestwood.md`
**Category:** Origin State Profile — Crestwood
**Purpose:** Reference profile for Crestwood. Paper title state. Contains "Reconstructed" brand (different from Halloway "Rebuilt"). DISTRACTOR for B5, B6.

**Must contain:**
- Header: Marion DMV — Origin State Reference Profile: CRESTWOOD
- Title system: PAPER. Crestwood issues physical titles. Standard practice: title held by lienholder if lien active; released to owner upon lien payoff.
- Lien procedures: Crestwood lien release = stamped endorsement on title face, signed by lienholder, OR separate Form CST-LR (Crestwood Lien Release) on lienholder letterhead.
- Emissions program: Crestwood operates a state emissions program. Not accepted in Marion.
- Brand terminology:
  | Crestwood Brand | Marion Equivalent |
  |---|---|
  | Salvage | Salvage |
  | Reconstructed | Reconstructed _(Crestwood "Reconstructed" = salvage vehicle rebuilt to roadworthy — equivalent to Marion "Reconstructed")_ |
  | Flood | Flood Damage |
  | Odometer Rollback | Odometer Rollback |
  | Lemon Law | Lemon Law Buyback |
- Tax: Crestwood rate 6.0%. Marion has a reciprocity agreement with Crestwood. Customer should bring documentation of tax paid in Crestwood (typically a receipt or the original purchase agreement with tax line itemized).
- Footer: `Marion DMV Origin State Profile — Crestwood | Rev. 2024-06 | Page 1 of 2`

**Eval coverage:** Supports A1 context (Crestwood paper title); no question has this as primary source.
**Distractor for:** B5 (Crestwood has separate "Salvage" and "Reconstructed" brands — might mislead system into splitting Pembrook "Salvage Rebuilt" into two brands), B6 (Crestwood 6% reciprocity row might be confused with Pembrook for retrieval).
**Messiness:** Crestwood tax documentation section describes "receipt or purchase agreement with tax line itemized" — slightly different from Admin Rule 7's description of required documentation (realistic discrepancy between origin-state profiles and Marion rules).

---

### 25. `origin-state-halloway.md`
**Category:** Origin State Profile — Halloway
**Purpose:** Reference profile for Halloway. Paper title, NO emissions program. "Rebuilt" maps to Marion "Reconstructed." PRIMARY DISTRACTOR for B1, B2.

**Must contain:**
- Header: Marion DMV — Origin State Reference Profile: HALLOWAY
- Title system: PAPER. Standard paper titles.
- Lien procedures: Halloway lien release = notarized endorsement on title face. Separate release letters not accepted by Halloway; Marion should require title-face endorsement.
- Emissions program: **Halloway does not operate a state emissions testing program.** Halloway vehicles are not required to undergo emissions testing for Halloway registration. _(← This is the distractor text for B1. Must be stated clearly so it can be retrieved, but the system must NOT use it to conclude Marion testing is skipped.)_
- Brand terminology:
  | Halloway Brand | Marion Equivalent |
  |---|---|
  | Rebuilt | **Reconstructed** _(IMPORTANT: Halloway "Rebuilt" means a vehicle rebuilt from salvage. Do NOT confuse with Verdana's "Rebuilt" designation, which maps to Marion "Rebuilt." See Brand Equivalency Guide.)_ |
  | Junk | Salvage |
  | Flood | Flood Damage |
  | Odometer | Odometer Rollback |
- Note embedded prominently: "Halloway's 'Rebuilt' brand is NOT the same as Verdana's 'Rebuilt' brand, despite using the same word. Halloway 'Rebuilt' maps to Marion 'Reconstructed.' Verdana 'Rebuilt' maps to Marion 'Rebuilt.' Always consult the Brand Equivalency Guide."
- Tax: Halloway rate 4.5%. Marion has a reciprocity agreement with Halloway.
- Footer: `Marion DMV Origin State Profile — Halloway | Rev. 2024-06 | Page 1 of 2`

**Eval coverage:** None — context only.
**Distractor for:** B1 (the "no emissions program" statement is the retrieval bait — system must override it with Marion's rule), B2 (contains "Rebuilt" → "Reconstructed" mapping, which is the WRONG answer when the title is from Verdana).
**Messiness:** The "Rebuilt" confusion note is present but may not chunk with the brand table if chunking splits before the note; "does not operate a state emissions testing program" is in a standalone sentence that will likely be a high-similarity hit for any emissions question.

---

### 26. `origin-state-pembrook.md`
**Category:** Origin State Profile — Pembrook
**Purpose:** Reference profile for Pembrook. Mixed title system. NO reciprocity. "Salvage Rebuilt" is a compound brand. DISTRACTOR for B5, B6.

**Must contain:**
- Header: Marion DMV — Origin State Reference Profile: PEMBROOK
- Title system: MIXED. Pembrook issues paper titles for most vehicles. Lienholder-participating lenders use Pembrook's ELT system (PemELT). Examiners may receive either a paper title or a PemELT Release Confirmation from Pembrook customers.
- Lien procedures: Paper lien release = lienholder endorsement on title face. PemELT release = electronic release confirmation from PemELT portal.
- Emissions program: Pembrook operates a state emissions testing program for vehicles registered in metro areas. Not accepted in Marion.
- Brand terminology:
  | Pembrook Brand | Marion Equivalent |
  |---|---|
  | Junk | Salvage |
  | **Salvage Rebuilt** | **Rebuilt** _(Pembrook's "Salvage Rebuilt" is a SINGLE compound designation. A vehicle declared salvage in Pembrook that has been rebuilt to roadworthy condition receives this single brand. Do NOT split into "Salvage" and "Rebuilt" brands on the Marion title. Map as one brand: Marion "Rebuilt.")_ |
  | Water Damage | Flood Damage |
  | Odometer | Odometer Rollback |
- Tax: **Pembrook has NO reciprocity agreement with Marion.** Customers transferring from Pembrook owe full Marion sales or use tax regardless of any tax paid in Pembrook. No credit is available. _(← Primary distractor text for B6. Must be unambiguous.)_
- Footer note: "Pembrook does not participate in Marion's tax reciprocity program. See Tax Reciprocity Schedule."
- Footer: `Marion DMV Origin State Profile — Pembrook | Rev. 2024-06 | Page 1 of 2`

**Eval coverage:** None — context only.
**Distractor for:** B5 ("Salvage Rebuilt" looks like two brands; Crestwood profile has separate Salvage and Reconstructed which makes this more confusing by proximity), B6 (Pembrook is in the same document category as Crestwood; reciprocity question retrieves both profiles; Pembrook's NO must override Crestwood's YES).
**Messiness:** "Salvage Rebuilt" in the table is formatted identically to the other single-word brands — only the note distinguishes it as compound; Pembrook PemELT adds realistic complexity about mixed paper/ELT that is not directly relevant to any eval question.

---

### 27. `form-tr1-instructions.md`
**Category:** Form Instructions — TR-1
**Purpose:** Field-by-field instructions for Form TR-1, the Out-of-State Title Transfer Application. Secondary source for A1, A8.

**Must contain:**
- Form title: Marion DMV Form TR-1 — Out-of-State Title Transfer Application
- When to use: Required for ALL out-of-state title transfers, purchase or relocation.
- Field instructions:
  - Block A: Customer information (name, address, DOB, license number).
  - Block B: Vehicle information (VIN, year, make, model, body type, color, odometer reading).
  - Block C: Transfer type — check PURCHASE or RELOCATION. _(If RELOCATION, leave Block D purchase price blank.)_
  - Block D: Purchase information (seller name and address, purchase price, date of sale) — PURCHASE only.
  - Block E: Origin state and origin title number.
  - Block F: Odometer disclosure — required for vehicles ≤ 10 model years old. For older vehicles: check EXEMPT box.
  - Block G: Insurance (policy number, insurer name, expiration date).
  - Block H: Lien information — if no lien, check NONE. If lien released, check RELEASED and attach release documentation. Active liens: do not complete TR-1; see supervisor.
  - Block I: Customer signature and date.
- Attachments required: origin title (or ELT Release Confirmation), Form TR-2, proof of insurance, bill of sale (PURCHASE) or residency proof (RELOCATION), sales tax payment or exemption documentation.
- Footer: `Form TR-1 Instructions | Rev. 2024-01 | Page 1 of 2`

**Eval coverage:** A1 (TR-1 is on the checklist), A8 (relocation uses TR-1 with RELOCATION selected).
**Distractor for:** None.
**Messiness:** Block H's "Active liens: do not complete TR-1" conflicts with the standard workflow where the examiner may start TR-1 before detecting the lien — realistic tension; Block F odometer disclosure appears near the end of the form but is mentioned in procedure chapters as item 6.

---

### 28. `form-tr2-instructions.md`
**Category:** Form Instructions — TR-2
**Purpose:** Instructions for Form TR-2, VIN Inspection Certification. Primary source for A6.

**Must contain:**
- Form title: Marion DMV Form TR-2 — VIN Inspection Certification
- When to use: Required for ALL out-of-state title transfers. Must be completed by a Marion-licensed VIN inspector, active law enforcement officer, or licensed Marion vehicle dealer. Customer cannot self-complete.
- Inspector qualifications:
  - Marion-licensed Motor Vehicle Inspector (license number begins "MVI-").
  - Active Marion law enforcement officer (badge number required).
  - Licensed Marion vehicle dealer (dealer number begins "DLR-").
- VIN locations to inspect:
  - Dashboard plate (driver's side, visible through windshield).
  - Driver-side door jamb sticker.
  - Engine block (if accessible without tools).
- Field instructions:
  - Block A: Inspector name, license/badge/dealer number, date of inspection.
  - Block B: Vehicle VIN as read from each location. All three must match. If discrepancy: check "DISCREPANCY" box and describe in Block D.
  - Block C: Inspector certification — signature confirming VINs match and vehicle description matches origin title.
  - Block D: Discrepancy notes (if any).
- Validity: TR-2 must be dated within 90 days of TR-1 submission date.
- If VIN discrepancy: examiner refers to supervisor per Admin Rule 5.2(e). Do not process transfer.
- Fee: $15.00 collected at DMV window (not paid to inspector separately).
- Footer: `Form TR-2 Instructions | Rev. 2024-01 | Page 1 of 1`

**Eval coverage:** A6 (VIN inspection form and process).
**Distractor for:** None.
**Messiness:** Fee ($15.00) stated in the form instructions rather than with the fee schedule — causes duplication; inspector qualification codes ("MVI-", "DLR-") are realistic noise.

---

### 29. `form-tr3-instructions.md`
**Category:** Form Instructions — TR-3
**Purpose:** Instructions for Form TR-3, Lien Release Acknowledgment.

**Must contain:**
- Form title: Marion DMV Form TR-3 — Lien Release Acknowledgment
- When to use: When a lienholder releases a lien via Marion DMV's registered lender system. Not required if lien is released by endorsement on the origin title face or by the origin state's separate lien release letter — in those cases, the release document itself is sufficient.
- Who completes: The lienholder (or Marion DMV on behalf of a registered lienholder). Not completed by the customer.
- When NOT to use: TR-3 is NOT a substitute for an origin-state lien release. If the origin title shows an unreleased lien and the lienholder has not registered with Marion DMV, TR-3 cannot be used. Supervisor referral required.
- Field instructions:
  - Block A: Lienholder name, Marion DMV lender registration number.
  - Block B: Vehicle VIN, year, make, model.
  - Block C: Lien information (original lien date, lien amount).
  - Block D: Release certification — lienholder signature confirming lien is paid in full and released.
  - Block E: Marion DMV acknowledgment stamp.
- Fee: $5.00 lien release processing fee.
- Footer: `Form TR-3 Instructions | Rev. 2024-01 | Page 1 of 1`

**Eval coverage:** Supports lien-released scenarios; no eval question directly targets TR-3.
**Distractor for:** None.
**Messiness:** "When NOT to use" section is important but small; fee ($5.00) appears only here and in the fee schedule — easy to miss.

---

### 30. `form-tr10-instructions.md`
**Category:** Form Instructions — TR-10
**Purpose:** Instructions for Form TR-10, Supervisor Exception Referral. Primary source (with procedure-ch5) for F1–F4.

**Must contain:**
- Form title: Marion DMV Form TR-10 — Supervisor Exception Referral
- When to use: Required whenever an examiner triggers a supervisor referral under Admin Rule 5.2. Must be completed before the supervisor is contacted. No fee.
- Completion instructions:
  - Block 1: Customer name, phone number, date of visit, examiner ID.
  - Block 2: Vehicle VIN, year, make, model, origin state, origin title number.
  - Block 3: Exception type (check all that apply):
    - ☐ Active lien — paper title
    - ☐ Active lien — ELT record
    - ☐ Branded title — recognized brand
    - ☐ Branded title — equivalent brand (origin term differs from Marion term)
    - ☐ Branded title — UNRECOGNIZED brand
    - ☐ VIN discrepancy
    - ☐ Missing or unverifiable origin documentation
    - ☐ Unrecognized origin state
    - ☐ Other (describe in Block 4)
  - Block 4: What the examiner has confirmed to date. Fill in as much as is known: lien holder name (if visible), brand name as it appears on title, ELT status.
  - Block 5: Supervisor disposition — leave blank. Supervisor completes with: CLEARED / PEND-DOCS / DENIED + reason.
- Conditional checklist: Examiner MAY attach a conditional document list to TR-10. Must be clearly marked: "CONDITIONAL — SUBJECT TO SUPERVISOR REVIEW. DO NOT ACT ON THIS LIST UNTIL SUPERVISOR CLEARS YOUR TRANSACTION."
- Turnaround: Supervisor must respond within 2 business days. If response not received, examiner follows up with supervisor's office directly.
- Customer communication: Do not tell the customer the specific exception type. Say: "We need to review some details with a supervisor; we'll contact you within 2 business days."
- Footer: `Form TR-10 Instructions | Rev. 2024-01 | Page 1 of 2`

**Eval coverage:** F1, F2, F3, F4 (TR-10 is the output form for all exception escalations).
**Distractor for:** None.
**Messiness:** Block 3 checkboxes use different phrasing than Admin Rule 5.2 (e.g., Rule says "Active lien on record"; TR-10 says "Active lien — paper title" and "Active lien — ELT record" as separate boxes — realistic inconsistency); customer communication script in plain prose at bottom, easy to miss.

---

## SQL seed data summary

See `test-data/sql/seed.sql` for full seed. Summary:

**vehicles table (10 records):**

| VIN | Origin | Title form | Lien status | Brand | Scenario |
|---|---|---|---|---|---|
| 1VRD0000001000001 | Verdana | ELT | NONE | CLEAN | ELT happy path |
| 1VRD0000001000002 | Verdana | ELT | ACTIVE | CLEAN | ELT + active lien → exception |
| 1CST0000001000001 | Crestwood | PAPER | NONE | CLEAN | Paper happy path (A1 source) |
| 1CST0000001000002 | Crestwood | PAPER | RELEASED | CLEAN | Released lien on paper |
| 1CST0000001000003 | Crestwood | PAPER | ACTIVE | CLEAN | Active lien → exception (F1 source) |
| 1CST0000001000004 | Crestwood | PAPER | NONE | Reconstructed | Recognized brand → exception |
| 1HAL0000001000001 | Halloway | PAPER | NONE | Rebuilt | Equivalent brand → exception (Halloway "Rebuilt" = Marion "Reconstructed") |
| 1HAL0000001000002 | Halloway | PAPER | NONE | CLEAN | No Halloway emissions + Marion emissions check |
| 1PMB0000001000001 | Pembrook | PAPER | NONE | CLEAN | No reciprocity tax scenario |
| 1PMB0000001000002 | Pembrook | PAPER | NONE | Salvage Rebuilt | Compound brand → exception |

**Edge cases:**
- `1HAL0000001000002`: odometer field NULL (tests missing-field tool path)
- `1PMB0000001000001`: insurance_expiry is stale (tests tool error path — "stale record")
- `1VRD9999999999999`: does not exist in DB (tests VIN-not-found path)

**fee_schedule table:** title_fee=25.00, registration_fee_light=45.00, registration_fee_heavy=65.00, vin_fee=15.00, emissions_fee=35.00, lien_release_fee=5.00

**tax_reciprocity table:**
- Verdana | has_agreement=true | rate=5.0
- Crestwood | has_agreement=true | rate=6.0
- Halloway | has_agreement=true | rate=4.5
- Pembrook | has_agreement=false | rate=NULL

**inspection_stations table:**
- Marion County: 2 stations (metro — emissions required)
- Riverside County: 2 stations (metro)
- Capital County: 1 station (metro)
- Dunmore County: 0 stations (rural — exempt)
- Alderton County: 0 stations (rural — exempt)
