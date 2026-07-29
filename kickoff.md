Starting a new build. Read new-project-instructions.md first.

USE CASE: Out-of-state vehicle title transfer assistant for a state DMV —
determines what a customer must produce to title and register a vehicle
brought in from another state, what blocks the transfer, and what it costs.

BUSINESS WORKFLOW IT AUTOMATES:
A customer arrives wanting to title a vehicle previously titled in another
state. Today a clerk classifies the scenario (relocation vs out-of-state
purchase), checks whether the origin state issues paper or electronic titles,
determines whether a lien is active and who holds the physical title, checks
whether the title carries a brand and whether this state recognises it under
that name, works out emissions and VIN inspection requirements by vehicle age
and county, computes sales tax owed net of credit for tax paid in the origin
state, and produces a document checklist and fee total. Exceptions — branded
titles, unreleased liens, missing origin documentation — go to a supervisor
before the customer is told anything.

END USER: DMV title examiner. A wrong answer means either a customer making a
wasted trip with the wrong documents, or a title being issued over an
undisclosed lien or an incorrectly carried brand — both of which are legal
exposure for the agency and downstream fraud risk for future buyers.

SYSTEMS OF RECORD: title and lien database, VIN decode service, tax
reciprocity schedule by state pair, fee schedule, inspection station
availability.

DOCUMENT CORPUS: title code statutes, administrative rules, examiner procedure
manual, brand-equivalency guide, tax reciprocity tables, form instructions.
Fictional jurisdictions so nothing contradicts real law — destination state
"Marion", plus origin states designed to exercise different branches: one
electronic-title, one paper-title, one with no emissions program, one using
different brand terminology.

PROVIDER: starting on <anthropic | ollama>

I want three things in this first pass:
1. Milestone 0 plan — domain model covering the transfer scenarios and their
   branching conditions, and a written definition of what "correct" means for
   a document checklist
2. The test-data spec per §3, including the eval questions BEFORE the
   documents that answer them. The origin-state documents should function as
   deliberate distractors for each other.
3. The day-one scaffold checklist per §2, adapted to this domain

Give me the plan first. Don't write code until I've agreed the domain model.

Use Plan Mode for this — I want to agree the domain model before any files
are created.