# PD-001 — Asset as the Governance and Discovery Hub

Status: PROPOSED  
Implementation: NOT_STARTED  
Date: 2026-09-22  
Owner: Product

## Context

DataOps already has several mature but partially overlapping ways to understand a data object:

- Asset owns a cross-domain governance ledger, catalog/taxonomy, listing state, owner, tags, health, reconciliation and a 360° detail surface.
- Metadata owns technical catalog truth for physical and projected entities, including schema/attributes, table/column relationships, change history, labels, lineage entry points and technical collection/reconciliation.
- Quality, Security, Lineage and Lifecycle each own their own domain facts.
- Source domains such as Modeling, Metric, Dataset, Dashboard and Development own the actual business object content.

The product problem is no longer “can we collect these facts”. The product problem is that a user should not need to understand which engineering module owns each fact in order to answer:

- what is this data object;
- who owns it;
- where it came from;
- whether it is trustworthy;
- whether it is sensitive;
- who uses it;
- what will be affected if it changes;
- what governance action is required next.

This decision defines the primary product entry point for those questions without changing domain truth ownership.

## Current Behavior

The current implementation already contains partial convergence, but it predates this Product Decision and does not count as an accepted product contract.

### Asset already behaves like a partial governance shell

Evidence:

- `yak-ops-business-asset/DOMAIN.md`
  - Asset Item is a cross-domain catalog card.
  - `asset_key` reuses source-domain / lineage identity.
  - business truth is read from the source domain rather than copied.
- `AssetDiscoverService`
  - aggregates source attributes;
  - 1-hop lineage;
  - security classification;
  - health;
  - view trend;
  - explicitly exposes Quality / TTL / Fields as UNAVAILABLE when those corridors are not ready.
- `AssetLifecycleService`
  - owns governance listing lifecycle (PENDING / PUBLISHED / OFFLINE / IGNORED / SOURCE_GONE);
  - requires precheck, owner/description/directory governance and audit evidence.
- `AssetOverviewService`
  - already behaves as a governance dashboard over catalog state, health and pending work.

### Provider coverage is already cross-domain

Current `AssetProvider` implementations include at least:

- Metadata physical tables
- Modeling models
- Metrics
- Datasets
- Analysis charts
- Dashboards
- Development tasks

This means Asset already has an identity/admission mechanism for multiple product domains rather than being a table-only catalog.

### Metadata is richer for technical discovery

Metadata currently provides capabilities that Asset should not duplicate:

- mixed-type technical search;
- physical table / column discovery;
- metamodel-driven attributes;
- children;
- source projection;
- technical change history;
- labels;
- lineage entry point;
- storage snapshot presentation.

The frontend `AssetExplorer` and `AssetDetailDrawer` already implement this technical catalog experience.

### Frontend already partially converges the entry point

The current Asset Catalog page includes two views:

- “台账资产”
- “元数据实体”

The metadata `AssetExplorer` is embedded inside the Asset Catalog page.

Navigation also places “元数据管理” under the “数据资产” top-level domain rather than as a separate top-level product domain.

### Important current gaps

Asset 360 is not yet complete:

- Quality section is explicitly UNAVAILABLE in `AssetDiscoverService`;
- Lifecycle TTL section is explicitly UNAVAILABLE;
- field/schema experience is not yet integrated into Asset detail;
- usage currently focuses on Asset-page views rather than full downstream business usage;
- some source types have stronger Provider coverage than others;
- governance status does not yet consistently affect downstream consumption.

Therefore the current code is evidence of direction, not proof that the Hub is complete.

## Decision

### Proposed decision

Adopt **Option A: Asset becomes the primary user-facing Governance & Discovery Hub**.

“Hub” means product entry point and aggregation boundary, not source-of-truth ownership.

Asset should answer the cross-domain user question:

> “What is this governed data object, what is its current governance state, and where do I go next?”

Asset must aggregate and link facts from source domains, but the owning domains retain their truth.

### Metadata role under this decision

Metadata remains the **technical catalog and metadata truth provider**, responsible for:

- metadata collection / registration / reconciliation;
- physical and projected entity catalog;
- table / column / schema / attribute facts;
- metamodel;
- technical change history;
- technical search primitives;
- technical entity detail.

Metadata discovery may be embedded or linked from Asset, but Asset must not reimplement Metadata storage or technical modeling.

### Source-domain ownership under this decision

Asset does not own:

- Model definition
- Metric definition / formula
- Dataset contract
- Workflow / Task definition
- Quality rule/execution truth
- Security decision truth
- Lineage graph truth
- Lifecycle policy truth
- Metadata schema/content truth

Asset owns only governance-specific facts such as:

- Asset ledger identity
- governance listing state
- governance owner/contact
- directory/taxonomy assignment
- business tags owned by Asset
- governance precheck
- derived health / summary projections
- governance activity/audit context

## Product Outcome

Primary Journey:

- J1 — External Data -> Governed Data Object
- J4 — Failure -> Impact -> Action
- J5 — Sensitive Data -> Safe Consumption

Expected product result:

A user should be able to start from one product entry point, find a governed data object, understand its governance context, and drill into technical/source-domain facts without learning module boundaries.

The user should not need to decide first whether the answer lives in Metadata, Quality, Security, Lineage, Lifecycle or Asset.

## Alternatives Considered

### Option A — Asset is the Governance & Discovery Hub

Asset is the user-facing catalog/360 shell; Metadata is the technical catalog provider; all source domains retain Truth.

Advantages:

- closest to current implemented direction;
- Asset already has cross-domain identity, provider SPI, governance lifecycle and 360 aggregation;
- avoids making technical Metadata semantics the product's governance semantics;
- preserves domain ownership cleanly;
- creates one natural place for owner, listing, health, tags, impact and next actions;
- supports non-physical objects such as Metric, Dataset, Dashboard and Development Task.

Trade-offs:

- Asset 360 must become much more complete;
- requires careful aggregation contracts instead of direct cross-domain imports;
- technical metadata detail must remain available without duplicating it;
- “Asset” terminology must be understandable for both technical and business objects.

### Option B — Metadata is the primary Discovery Hub; Asset is only a governed listing layer

Metadata becomes the main search/detail experience. Asset only manages publish/listing/taxonomy/owner state.

Advantages:

- Metadata already has strong mixed-type technical search and rich entity detail;
- fewer catalog-like experiences at first glance.

Trade-offs:

- Metadata must represent non-metadata governance concepts it does not own;
- governance state becomes secondary to technical catalog state;
- risks turning Metadata into a second control plane;
- business objects such as Dataset, Metric, Dashboard and Task become projections inside a technical catalog model;
- contradicts the current Asset Provider / governance lifecycle investment.

### Option C — No hub; keep federated domain experiences + global search

Each domain owns its own detail. A search layer only routes users to the correct domain.

Advantages:

- minimal aggregation coupling;
- strongest local-domain autonomy.

Trade-offs:

- preserves the current fragmented user mental model;
- no single place answers “is this object governed/trustworthy/usable?”;
- cross-domain governance workflows remain navigation-heavy;
- every future consumer needs to reconstruct Quality/Security/Lineage/Lifecycle context.

## Consequences

### Positive

- one primary discovery/governance mental model;
- source-domain truth remains intact;
- Metadata can stay technically deep without becoming a generic governance product;
- future Quality/Security/Lineage/Lifecycle work has a clear place to surface its result;
- Provider-based source registration becomes a reusable product corridor;
- Asset health/listing/owner/tags become meaningful because users can see them beside source facts.

### Trade-offs

- Asset becomes strategically important and must be treated as a stable product boundary;
- more cross-domain query APIs / gateways will be required;
- partial or unavailable facts must remain visible rather than being faked;
- product design must avoid an “everything page” with too many tabs/cards;
- source-domain deep editing should still happen in the owning product.

### Risks

1. **Second truth risk**  
   Asset may start copying Metadata/Quality/Security/Lineage facts for convenience.

   Mitigation: only store governed snapshots / derived summaries explicitly owned by Asset; live detail stays with owning APIs.

2. **God-module risk**  
   Asset may absorb business logic from every domain.

   Mitigation: Asset aggregates; it does not command other domains except through explicit product workflows accepted separately.

3. **Duplicate detail UX**  
   Asset 360 and Metadata Entity Detail can drift into two competing “details”.

   Mitigation: define their jobs:
   - Asset Detail = governance/context/action shell;
   - Metadata Detail = technical structure and metadata diagnostics.

4. **Incomplete corridor risk**  
   A single hub with many UNAVAILABLE sections may feel worse than separate mature screens.

   Mitigation: roll out section-by-section with honest availability and only promote F-001 when critical corridors are defined.

## Truth / Ownership Impact

- Asset Truth Owner:
  - governance ledger;
  - listing lifecycle;
  - governance owner/contact;
  - Asset taxonomy/tags;
  - governance precheck;
  - derived health and view/summary projections.
- Metadata Truth Owner:
  - technical catalog entities and metadata attributes.
- Quality Truth Owner:
  - quality configuration/execution/result.
- Security Truth Owner:
  - classification and access/masking decisions.
- Lineage Truth Owner:
  - graph assets/relations and impact graph.
- Lifecycle Truth Owner:
  - retention/storage lifecycle policy and execution.
- Source domains:
  - business object definition/content.

Cross-domain facts are referenced, not copied as new business truth.

## Navigation / UX Impact

If accepted, this decision does **not** immediately authorize a navigation redesign.

Target product intent:

- “Data Asset / Asset Catalog” is the primary user entry for discovery and governance context.
- Metadata collection/reconciliation remains an expert/admin capability.
- Metadata technical search/detail can be embedded or deep-linked from Asset.
- Quality/Security/Lineage/Lifecycle keep their own specialist workspaces for configuration/operations.

A future decision (for example PD-003) should separately decide whether the top-level “Data Governance” and “Data Asset” navigation domains merge.

## Migration Plan

No migration is authorized while Status = PROPOSED.

If ACCEPTED:

### Phase 1 — Contract

Create `F-001-asset-360.md` defining:

- exact Asset 360 information architecture;
- required sections and owning APIs;
- user stories;
- success metrics;
- section availability rules;
- cross-domain deep links;
- non-goals.

### Phase 2 — Critical corridors

Prioritize:

1. Metadata technical detail corridor;
2. Quality summary corridor;
3. Security summary / decision corridor;
4. Lineage + impact corridor;
5. Lifecycle/retention corridor;
6. real usage/consumer corridor.

### Phase 3 — Product entry convergence

Only after F-001 acceptance evidence:

- reduce duplicate discovery entry points;
- make Metadata expert tools contextual to Asset where appropriate;
- evaluate navigation convergence in a separate Product Decision.

## Acceptance Evidence

The Decision can move to `Implementation: DONE` only when:

1. A user can find a governed object from the Asset entry without first choosing an engineering domain.
2. Asset detail clearly distinguishes:
   - Asset-owned governance facts;
   - live facts from Metadata / Quality / Security / Lineage / Lifecycle / source domain.
3. No cross-domain business fact is duplicated as a second Truth Owner.
4. For at least the critical object classes (physical table, Model, Metric, Dataset), the 360 view provides working source/context deep links.
5. UNAVAILABLE / EMPTY / NOT_APPLICABLE are distinguishable and not collapsed into false “healthy” states.
6. A user can navigate from a governance issue to the owning specialist workflow and back to the Asset context.
7. E2E tests or recorded acceptance evidence prove the above on `main`.

## Non-goals

This Decision does not decide:

- Dataset as the default consumption contract (separate PD);
- merge of Data Governance and Data Asset top-level navigation;
- whether MDM is a Solution Pack;
- Approval product positioning;
- Metric consumption model;
- whether governance state blocks consumption;
- detailed Asset 360 page layout;
- technical implementation of missing Quality/Lifecycle corridors.

## Supporting Evidence

Primary repository evidence:

- `yak-ops-business/yak-ops-business-asset/DOMAIN.md`
- `yak-ops-business/yak-ops-business-asset/REQUIREMENTS.md`
- `yak-ops-business/yak-ops-business-asset/application/AssetDiscoverService.java`
- `yak-ops-business/yak-ops-business-asset/application/AssetLifecycleService.java`
- `yak-ops-business/yak-ops-business-asset/application/AssetOverviewService.java`
- current `AssetProvider` implementations in Metadata / Modeling / Metric / Dataset / Analysis / Dashboard / Development
- `yak-ops-business/yak-ops-business-metadata/ARCHITECTURE.md`
- `MetadataSearchController.java`
- `MetadataEntityController.java`
- `yak-ops-ui/src/pages/data-asset/catalog/index.tsx`
- `yak-ops-ui/src/pages/data-asset/detail/index.tsx`
- `yak-ops-ui/src/pages/data-metadata/components/AssetExplorer.tsx`
- `yak-ops-ui/src/pages/data-metadata/components/AssetDetailDrawer.tsx`
- `yak-ops-ui/src/config/navigation.ts`
- `docs/reviews/productization-review-v1.md`

## Supersedes

None
