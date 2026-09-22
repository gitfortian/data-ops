# DataOps Productization Review V1

> Class: Evidence / Product Review  
> Date: 2026-09-22  
> Authority: this document does not override docs/product. Accepted decisions must be promoted into Product Truth before implementation.

## 1. Review Goal

DataOps already has broad engineering coverage. The current product risk is not missing modules; it is fragmentation:

- users must understand module boundaries to complete one task;
- the same data object is explained across multiple back-office pages;
- governance configuration does not always affect execution or consumption;
- engineering modules can accidentally become product navigation;
- individual modules can be locally complete while the end-to-end journey remains broken.

The productization goal is:

> move from module completeness to journey closure.

## 2. Product Shape

The 28 engineering/product nodes should not become 28 user mental models.

Recommended product shape:

~~~text
1. Data Integration
2. Standards, Metrics & Modeling
3. Development & Orchestration
4. Data Assets & Governance
5. Consumption & Service
6. Professional Solution Packs
   - MDM
~~~

Cross-cutting platform capabilities remain underneath:

~~~text
Project Space / RBAC
Approval / Audit / Alert
Task Runtime / Plugin / Scheduler
~~~

Home is a workspace/overview, not a business domain.

## 3. 28-Node Product Classification

| Engineering / Current Node | Product Capability | Product Role | Product Surface Decision |
|---|---|---|---|
| datasource | Data Integration | Core product surface | Keep visible; primary external-data entry |
| resource | Data Integration | Supporting surface | Keep nested; do not grow into a separate product domain |
| sync-offline | Data Integration | Core product surface | Keep visible as data integration mode |
| sync-realtime | Data Integration | Core specialist surface | Keep visible but share integration mental model with offline |
| metadata | Data Assets & Governance | Engine + governance admin surface | Keep collection/admin functions; discovery should converge into Asset |
| semantic | Standards, Metrics & Modeling | Strategic core | Keep visible; own business standards and common semantics |
| metric | Standards, Metrics & Modeling | Strategic core | Keep visible; must become consumable, not remain a definition ledger |
| modeling | Standards, Metrics & Modeling | Strategic core | Keep visible; connect standards/metrics to physical/data models |
| data-development | Development & Orchestration | Core product surface | Keep visible |
| task-catalog | Development & Orchestration | Internal platform capability | No independent product navigation |
| job | Development & Orchestration | Internal runtime capability | Hide from ordinary product mental model |
| workflow | Development & Orchestration | Core product surface | Keep visible |
| dataset | Consumption & Service | Strategic hub | Raise product status; default consumption contract |
| analysis | Consumption & Service | Embedded authoring capability | Prefer embedding into Dashboard/analysis workflow over standalone product |
| dashboard | Consumption & Service | Core product surface | Keep visible |
| digital-screen | Consumption & Service | Scenario surface | Keep, but share common publish/query/building blocks with Dashboard |
| data-service | Consumption & Service | Core product surface | Keep visible; prefer Dataset-governed source path |
| agent | Consumption & Service | Strategic consumption entry | Keep; natural-language interface over governed data/evidence |
| home | Product shell | Read-only workspace | Keep; no Truth ownership and no second analytics engine |
| quality | Data Assets & Governance | Core governance capability | Keep visible; surface conclusions in Asset 360 and runtime incidents |
| security | Data Assets & Governance | Core governance capability | Keep visible; value only when decisions affect actual consumption |
| asset | Data Assets & Governance | Strategic hub | Raise product status; become 360-degree governance/discovery entry |
| lineage | Data Assets & Governance | Shared governance capability | Keep capability; embed contextually in Asset/Model/Metric/Dataset |
| approval | Cross-cutting platform | Work-center capability | Keep To-do entry; do not behave as equal business product domain |
| audit | Cross-cutting platform | Evidence capability | Consolidate views; usually reached contextually or from admin/ops |
| alert | Cross-cutting platform | Notification capability | Do not grow into standalone product domain; expose rules/channels where needed |
| lifecycle | Data Assets & Governance | Specialist governance capability | Keep nested under governance |
| mdm | Professional Solution Pack | Vertical solution | Keep isolated as solution pack; must reuse platform capabilities |

## 4. Product Hubs

Four objects should connect the product instead of allowing every module to integrate directly with every other module.

### 4.1 Asset — governance hub

Asset should answer:

- what is this data object;
- owner / domain / description;
- metadata and schema;
- standardization status;
- quality conclusion;
- security classification;
- lineage;
- usage;
- lifecycle;
- publish/listing state.

Asset aggregates facts; source domains keep their Truth.

### 4.2 Dataset — consumption hub

Preferred consumption flow:

~~~text
Production -> Dataset -> Dashboard / API / Agent / Analysis
~~~

Direct SQL or raw DataSource consumption can exist as advanced paths, but should not be presented as equivalent governed defaults.

### 4.3 Metric — business-measure hub

Metric becomes valuable only when it can be:

- defined consistently;
- validated/previewed;
- referenced by consumption products;
- traced to source models;
- queried for usage;
- impact-analyzed after upstream change.

### 4.4 Semantic — shared business-language hub

Semantic owns common business vocabulary and standards.

Other modules reference it; they do not create local synonyms for business domain/process/caliber/unit/security-standard concepts.

## 5. Journey Gap Review

### J1 — External Data -> Trusted Asset

Current strong parts:

- datasource;
- offline sync;
- workflow/runtime backbone;
- quality execution exists.

Main product gaps:

- Metadata -> Asset/Model discovery experience is still split;
- Model physicalization/reconciliation is incomplete;
- governance state is not consistently enforced in downstream use;
- Asset coverage is not yet complete across all relevant domains.

Productization direction:

> one data object should be followable from connection/import to Asset 360 without knowing module boundaries.

### J2 — Business Definition -> Unified Metric

Current strong parts:

- semantic references;
- metric definitions/version/dependency;
- metric/model relationship;
- lineage foundation.

Main product gaps:

- Metric consumption is only partially connected;
- value preview/calculation is weak or absent;
- usage/impact is not yet a complete consumer loop.

Productization direction:

> stop treating Metric completion as CRUD/version completion; success is real consumption with usage/impact feedback.

### J3 — Production -> Dataset -> Consumption

This is the highest-leverage product convergence path.

Main gaps:

- Data Service can bypass Dataset/governed consumption;
- Analysis has weak standalone product identity;
- Dashboard/Digital Screen have duplicated publication/building concepts;
- different consumption products can build local aggregation semantics.

Productization direction:

> Dataset becomes the preferred data contract; Analysis becomes an authoring capability; consumer products share common governed bindings.

### J4 — Failure -> Impact -> Action

Current pieces exist in workflow/runtime, quality, alert, lineage, asset and audit, but the user journey is not one coherent incident flow.

Desired path:

~~~text
Failure / Quality issue
 -> affected Asset
 -> upstream/downstream Lineage
 -> owner / consumers
 -> rerun/fix/action
 -> alert + audit evidence
~~~

### J5 — Sensitive Data -> Safe Consumption

Current configuration surface is broader than current execution enforcement.

Desired path:

~~~text
Discovery
 -> classification
 -> access/masking policy
 -> Dataset/API/Agent query
 -> allow/mask/deny
 -> audit
~~~

The product is incomplete until the policy changes real query results or access.

### J6 — Question -> Evidence-backed AI Answer

The Agent architecture has the correct direction because it goes through Dataset contracts and Evidence.

Next product convergence should be:

~~~text
Question
 -> Semantic / Metric discovery
 -> Dataset
 -> Evidence
 -> Answer / Report
 -> trace back to Asset / Metric / Dataset
~~~

Agent should become a consumer of product truth, not a parallel data platform.

## 6. Navigation Review

Current top-level business navigation is still close to the engineering organization.

Long-term product target should reduce user mental domains.

Proposed target shape for review:

~~~text
Home / My Work

Data Integration
  Data Sources
  Offline / Realtime Integration
  Resources

Standards & Modeling
  Data Standards
  Metrics
  Modeling

Development & Orchestration
  Development
  Workflow
  Runtime / Instances

Data Assets & Governance
  Asset Catalog
  Metadata Collection
  Lineage
  Quality
  Security
  Lifecycle
  MDM or Solution Pack entry

Consumption & Service
  Dataset
  Dashboard
  Digital Screen
  API
  Agent

System
~~~

Approval To-do should behave like My Work / task center. Approval flow configuration belongs to administration/governance configuration rather than an equal product domain.

This is a proposed product decision, not an implementation instruction.

## 7. Stop / Merge / Raise Decisions to Review

### Raise

- Asset: from governance module to platform governance hub.
- Dataset: from one consumption module to default consumption contract.
- Metric: from definition ledger to consumable business measure.
- Semantic: from isolated standards module to cross-domain language owner.
- Agent: from isolated AI feature to governed consumption entry.

### Embed / Merge product experience

- Analysis -> Dashboard authoring flow.
- Metadata discovery/search -> Asset Catalog / Asset 360.
- Lineage -> contextual entry from Asset/Model/Metric/Dataset plus global view.
- Audit -> contextual evidence + consolidated admin view.
- Alert -> notification/routing capability used by runtime/quality, not a product island.
- Approval -> My Work + reusable approval capability.

### Keep but control scope

- Digital Screen: scenario product, avoid second BI stack.
- MDM: professional solution pack, reuse sync/quality/approval/service/asset/lineage.
- Lifecycle: specialist governance surface, do not broaden into generic asset management.

### Hide from normal product mental model

- Job runtime.
- Task Catalog internals.
- Trigger/runtime adapters and engine-specific mechanics.

## 8. First Productization Backlog

Priority is based on journey closure, not module maturity.

### P0 — Establish the two hubs

1. Asset 360 convergence:
   - metadata
   - standards
   - quality
   - security
   - lineage
   - usage
   - lifecycle

2. Dataset consumption contract:
   - define governed default path;
   - classify existing raw/direct consumption paths;
   - make Dashboard/API/Agent relationships explicit.

### P0 — Close Metric consumption

3. Metric -> Dataset/Dashboard/API usage relationship.
4. Metric value preview/validation path.
5. Upstream change -> downstream impact/usage visibility.

### P1 — Make governance executable

6. Security decision enters Dataset/API/Agent runtime.
7. Asset listing/governance state influences publish/consumption where product rules require it.
8. Quality incident links to Asset + Lineage + owner/action.

### P1 — Product surface convergence

9. Merge Data Governance + Data Asset user mental model.
10. Reposition Approval as My Work / cross-cutting capability.
11. Reposition Analysis as authoring capability.
12. Consolidate Audit and Alert entry points.

### P2 — Solution-pack discipline

13. MDM reuse audit: remove duplicated quality/service/sync behaviors where platform capabilities already exist.
14. Digital Screen/Dashboard shared product building blocks.

## 9. Product Decisions Required Before Implementation

The following should be explicitly accepted or rejected and then promoted into Product Truth:

- D-P01: Asset is the governance/discovery hub.
- D-P02: Dataset is the default governed consumption contract.
- D-P03: Data Governance and Data Asset converge into one user mental domain.
- D-P04: Approval is a cross-cutting work capability, not an equal business domain.
- D-P05: Analysis becomes an embedded authoring capability rather than a standalone product.
- D-P06: MDM is a professional solution pack, not DataOps Core.
- D-P07: Metric completion means real consumption + usage/impact, not only definition/version.
- D-P08: Agent is a governed consumption entry and must not create a parallel data-access truth.

Until these are accepted, this review remains Evidence and should not directly trigger navigation or module deletion.

## 10. Success Criteria for Productization

DataOps is productized when users can complete core journeys without understanding engineering module boundaries.

The measurable signs are:

- fewer top-level product concepts;
- more contextual cross-links between core objects;
- one Truth Owner per concept;
- governance changes real execution/consumption behavior;
- Dataset/Asset/Metric/Semantic act as stable cross-domain hubs;
- Feature completion is proven by E2E journey evidence.
