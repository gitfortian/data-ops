# Existing Documentation Classification Index

This is a provisional classification of existing repository documentation. It does not move or delete files.

Its purpose is to tell humans and AI how to interpret them while the product baseline is being rebuilt.

| Path / Family | Class | Current Use |
|---|---|---|
| `PRODUCT_STYLE.md` | Product Truth / Process | repository product-development rules |
| `docs/product/**` | Product Truth | authoritative cross-domain product baseline |
| module `DOMAIN.md` | Domain Contract | stable domain invariants |
| module `REQUIREMENTS.md` | Domain Contract | stable module behavior requirements |
| module `ARCHITECTURE.md` | Architecture Contract | package / role / truth / runtime boundary |
| module `DEPENDENCIES.md` | Architecture Contract | dependency corridor |
| root `CODE_STYLE.md` | Architecture / Engineering Contract | repository engineering rules |
| `docs/architecture/**` | Architecture Contract or Evidence | keep only explicitly referenced stable contracts authoritative |
| `docs/release/**` | Operational Contract | release procedure |
| `docs/PLATFORM_CORE_FLOW.md` | Evidence / predecessor product baseline | source material to reconcile into current Product Truth |
| `docs/INTERACTION_PRINCIPLES.md` | UX Evidence / guideline | keep as design input; promote durable principles explicitly |
| `docs/MENU_REDESIGN.md` | Delivery / UX Evidence | historical navigation design input |
| `docs/home-overview-contract.md` | Domain / UX Evidence | reconcile with Home product rules before implementation |
| `docs/v1/**` | Evidence / Review | major source of product audit findings, not final authority |
| `docs/test/**` | Evidence | acceptance and defect evidence |
| `docs/prototypes/**` | Design Evidence | interaction/reference prototypes |
| `docs/*/review*.md` | Evidence / Review | observations and recommendations |
| `docs/*/dev-plan.md` | Delivery Plan | temporary implementation planning |
| `docs/*/issues/**` | Work Items | implementation tickets and acceptance notes |
| `docs/*/gap-backlog*.md` | Evidence / Backlog | gaps requiring explicit promotion before implementation |
| `docs/multi_version*` | Architecture / Delivery Evidence | reconcile before treating as current versioning policy |
| `docs/project-space/**` | Architecture / Migration Evidence | use current implemented Project Space contracts as authority |
| module `README.md` | Orientation | useful entry point; lower authority than contracts above |
| module `REVIEW.md` | Review Policy / Evidence | review checklist, not product requirement owner |

## Rules for the next cleanup

Do not mass-move documents yet.

For each family:

1. identify durable facts;
2. promote them into the correct owner document;
3. update references;
4. only then archive or move obsolete material.

Physical cleanup comes after semantic cleanup.
