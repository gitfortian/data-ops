# Phase8 Runtime Test Matrix

| Rule | Domain | Scenario | Expected |
| --- | --- | --- | --- |
| TableNullRateRule | Quality | null rate below threshold | PASS |
| TableNullRateRule | Quality | null rate exceeds threshold | Finding |
| SensitiveColumnRule | Security | protection enabled | PASS |
| SensitiveColumnRule | Security | protection missing | Finding |
| ExpiredAssetRule | Lifecycle | asset active | PASS |
| ExpiredAssetRule | Lifecycle | asset expired | Finding |

## Runtime Chain

RuleContext -> Provider -> Engine -> Result -> Finding
