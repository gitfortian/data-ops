# AI / Agent Repository Entry

This file is the repository entry point for AI coding and review agents.

Before changing business behavior, read in this order:

1. `PRODUCT_STYLE.md`
2. `docs/product/README.md`
3. Related Product Spec, if one exists
4. Target module `DOMAIN.md`
5. Target module `REQUIREMENTS.md`
6. Target module `ARCHITECTURE.md`
7. Target module `DEPENDENCIES.md`
8. Root `CODE_STYLE.md`

## Mandatory behavior

Do not start from tables, controllers, pages, or classes.

First determine:

- user
- problem
- capability
- user journey
- expected outcome
- truth owner
- producer / consumer
- reuse of existing platform capabilities
- E2E acceptance evidence

Do not create a new module, menu, state machine, business term, or duplicate source of truth merely to make a local module look complete.

If historical documents conflict with the current Product Baseline, follow `docs/product/DOCUMENT_GOVERNANCE.md` and surface the conflict explicitly.

For analysis-only tasks, historical reviews and gap reports are useful evidence. For implementation decisions, they are not automatically authoritative.
