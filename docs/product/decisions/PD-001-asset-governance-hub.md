# PD-001 — Asset as Governance & Discovery Hub

Status: PROPOSED  
Date: 2026-09-22  
Owner: Product

## Context

DataOps already has metadata, quality, security, lineage, lifecycle, approval, audit and usage-related capabilities. Today these facts are distributed across separate modules and pages.

From a user perspective, the central question is not “which module owns this field”, but:

> What is this data object, is it trustworthy, where did it come from, who owns it, and who is using it?

Without a product-level aggregation hub, users must understand module boundaries before they can understand one data object.

## Decision

Make **Asset** the default governance and discovery hub for governed data objects.

Asset does **not** become the owner of every governance fact.

Instead:

- source domains keep their Truth;
- Asset owns the governed object identity / catalog / lifecycle state that belongs to the asset domain;
- Asset 360 aggregates authoritative facts from Metadata, Semantic, Quality, Security, Lineage, Usage, Lifecycle and related domains;
- contextual navigation should allow users to move from Asset to the owning source domain and back.

The preferred product experience becomes:

~~~text
Metadata
Semantic
Quality
Security
Lineage
Usage
Lifecycle
   \
    -> Asset 360 -> user understands and acts on the data object
~~~

## Product Outcome

Improve J1 “External Data -> Trusted Asset” and J4 “Failure -> Impact -> Action”.

A user opening one governed object should be able to understand:

- identity and description;
- owner and business domain;
- schema / metadata;
- standardization status;
- quality conclusion;
- security classification;
- lineage;
- usage;
- lifecycle;
- publish/listing state;
- relevant actions and source-domain links.

## Alternatives Considered

### Option A — Keep each governance module as an equal product destination

Pros:
- simple ownership boundaries;
- minimal product convergence work.

Cons:
- users reconstruct the asset picture manually;
- governance feels like multiple back-office products;
- cross-module navigation remains weak;
- AI and human users need to know module boundaries.

### Option B — Copy all governance facts into Asset

Pros:
- easy rendering;
- one database-shaped view.

Cons:
- creates duplicate Truth;
- high synchronization and drift risk;
- violates existing domain ownership discipline.

### Option C — Asset as aggregation hub, domains keep Truth

This proposal.

It preserves domain ownership while creating one product entry for discovery and governance context.

## Consequences

### Positive

- lowers user mental load;
- makes governance visible around the data object instead of around modules;
- creates a stable place for 360-degree discovery;
- gives Agent and future search/discovery flows a clear governed-object entry;
- supports contextual actions from one object.

### Trade-offs

- Asset needs more stable read contracts with source domains;
- page performance and partial availability need explicit design;
- source-domain links and fallback states must be consistent;
- existing standalone governance pages still need clear admin/operational roles.

### Risks

- Asset may accidentally become a shadow copy of every domain;
- teams may push business logic into Asset for convenience;
- provider coverage can create a false sense of completeness if status is not visible.

## Truth / Ownership Impact

- Asset Truth Owner: asset identity, catalog organization, asset-owned state and listing lifecycle.
- Metadata Owner: metadata entity facts and schema-related metadata.
- Semantic Owner: standards, business domains/processes, standard fields.
- Quality Owner: quality rules, executions and conclusions.
- Security Owner: security classification, access/masking policy decisions.
- Lineage Owner: lineage relationships.
- Lifecycle Owner: retention / lifecycle policies and execution facts.
- Usage Owner: remains with the domain that owns the usage fact; Asset aggregates read views.

Asset may cache/project data for product rendering only if projection ownership and refresh semantics are explicit.

## Navigation / UX Impact

If ACCEPTED:

- “Data Asset” becomes the primary discovery/governance entry.
- Metadata search/discovery should converge into Asset Catalog / Asset 360.
- Global Lineage may remain as a specialist view, but Asset offers contextual lineage.
- Quality/Security/Lifecycle remain management surfaces while Asset shows conclusions and entry links.
- users should not need to visit several governance modules just to understand one object.

No navigation change should be implemented by this proposal alone.

## Migration Impact

Phased:

1. inventory current Asset providers and missing domains;
2. define read contracts and partial-state semantics;
3. build Asset 360 composition model;
4. add contextual links to source domains;
5. move discovery/search experiences where appropriate;
6. only then simplify redundant navigation.

## Acceptance Evidence

An end-to-end scenario is complete when a user opens one representative table/model/dataset asset and can see, from one Asset 360 experience:

- metadata identity;
- standards status;
- latest quality conclusion;
- security classification;
- upstream/downstream lineage;
- downstream usage;
- lifecycle policy;
- responsible owner;
- source links that open the authoritative owning module.

No copied business truth is introduced solely to render the page.

## Non-goals

- Asset does not replace Metadata.
- Asset does not own Quality/Security/Lineage rules.
- Asset does not become a generic workflow engine.
- This decision does not require deleting existing governance pages.

## Supersedes

None
