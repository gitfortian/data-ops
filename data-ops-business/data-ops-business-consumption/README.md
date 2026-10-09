# Governed Consumption boundaries

Authority: ACCEPTED PD-002 and APPROVED F-004 in docs/product.

Consumption owns Subscription and normalized Usage Evidence. Dataset/Data Service own source
definition and immutable release/revision. Asset owns its ledger; Quality, Security and Lineage
retain their own evidence. No projection writes source state.

Canonical detail reads Quality/Security/Lineage through AssetDiscoverService with the authenticated
operator. It maps OK/EMPTY/UNAVAILABLE/PERMISSION_DENIED/NOT_APPLICABLE to the Consumption five
states, discards facts on unreadable states, and isolates each failed section. Readable evidence
identifies its indexed source asset and observation time. Physical-table Quality is not automatically
inherited by a Dataset or Data Service. Asset governance contacts do not fill source owner/visibility.

Dataset Access checks the action permission. Denial is FORBIDDEN; action permission alone leaves
the physical-column query decision UNAVAILABLE with the exact-query next step. Query execution
continues to use the Dataset-owned SQL projection, security policy, masking and audit gate.
Access never creates successful Usage.

Boot projects Yak CurrentUser into MVC Principal only for Dataset/Consumption console APIs.
The role codes come from the owning RoleService; unavailable roles stop the request rather than
dropping ROLE policies. Neither client identity headers nor a public Consumer key becomes a USER.

Standalone SQL Dataset producer navigation uses DevelopmentDatasetFacade owning provenance;
QUERY_REVISION uses the fixed Task Catalog source. Neither uses name matching.

DataServiceAssetProvider is a read-only adapter from the source Reader into AssetProvider SPI.
Cursor batches are bounded to 500 and must match CurrentProject; refresh is also project scoped.
The source-owned DataServiceIdentity key survives path/name/revision/runtime changes. The
descriptor excludes SQL, raw keys and connection parameters. Asset does not depend on this module.

Subscription/Usage/Impact remain independent. Known consumers are bounded evidence, not a claim
of every external dependency. F-004 is not SHIPPED until both real journeys and its full matrix pass.

F-035 read-only Asset/Agent consumption uses ConsumerUsageSummaryReader, not ConsumerImpactService.view.
It reads only persisted current-project/product ACTIVE Subscription and successful normalized Usage windows,
at most 200 rows per side in descending timestamp/id order. It never reconciles, normalizes or saves.
The existing Consumption detail journey retains source reconciliation. Window limits, LIMIT_REACHED /
WITHIN_LIMIT / UNKNOWN and independent provider states are evidence scope, not a business lifecycle.
Unreadable side-specific counts/times stay null; the known-consumer union covers readable windows only.
NOT_PERFORMED and unknown freshness must remain visible. Empty or unsaturated persisted windows never prove
complete source history. Asset continues to consume SPI; no reverse Maven dependency or new source truth.

F-036 ConsumerVersionImpactQueryApi is the narrow read-only Agent boundary. It checks Asset READ and
CurrentProject/source-product identity before bounded persisted version usage and ACTIVE subscriptions.
Each side is at most 10 rows; a current active reference or normalized exact-version success proves only
version membership, never the historical definition or completeness. No reconciliation or writes.
Only tagged Consumer identities and scoped counts/times cross the API; no actors, display hints,
provider refs or source configuration. Agent depends on consumption.api, never the implementation.
