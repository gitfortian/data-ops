#!/usr/bin/env python3
"""IA-550 v0.8.2 browser evidence runner (does NOT test deployed E2E).

Usage:
  python -m pip install playwright
  python -m playwright install chromium
  python scripts/architecture/ia550-v082-browser-signoff.py

Writes screenshots + result.json to build/ia550-v082-browser-signoff/.
A failure is recorded, never silently waived.
"""
from __future__ import annotations

import json
import platform
import subprocess
import sys
import traceback
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HTML = ROOT / "docs/product/experience/prototypes/IA-550-task-flows-v0.8.2-final-candidate.html"
OUTPUT = ROOT / "build/ia550-v082-browser-signoff"
WIDTHS = (320, 390, 768, 1024, 1366, 1440)
PAGES = ("home", "model", "product", "collab", "metric", "semantic", "standard", "asset", "governance", "quality", "security", "mdm")
SCREENSHOT_PAGES = {"home", "model", "product", "collab", "standard"}


def revision() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "UNKNOWN—set commit in attached evidence"


def main() -> int:
    if not HTML.is_file():
        print("BLOCKED_ENV: missing actual v0.8.2 prototype", file=sys.stderr)
        return 2
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("BLOCKED_ENV: pip install playwright; python -m playwright install chromium", file=sys.stderr)
        return 2

    OUTPUT.mkdir(parents=True, exist_ok=True)
    evidence = {
        "date_utc": datetime.now(timezone.utc).isoformat(),
        "prototype": str(HTML.relative_to(ROOT)),
        "commit": revision(),
        "python": sys.version.split()[0],
        "os": platform.platform(),
        "browser": "chromium",
        "browser_version": None,
        "checks": [],
        "summary": {},
        "limitations": [
            "Static self-contained HTML uses simulated data, not production API/real identity",
            "Manual screen reader, long data, old route, multi-project/RBAC and deployed E2E remain independent gates",
            "This run never certifies actual DataDev/Query/Invoke/Audit/Usage",
        ],
    }

    def check(label: str, operation) -> None:
        try:
            detail = operation()
            evidence["checks"].append({"id": label, "status": "PASS", "details": detail})
            print("PASS", label)
        except Exception as exc:
            evidence["checks"].append({
                "id": label,
                "status": "FAIL",
                "reason": f"{type(exc).__name__}: {exc}",
                "trace": traceback.format_exc(limit=3),
            })
            print("FAIL", label, str(exc))

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        evidence["browser_version"] = browser.version
        for width in WIDTHS:
            context = browser.new_context(viewport={"width": width, "height": 900}, device_scale_factor=1)
            page = context.new_page()
            js_errors = []
            page.on("pageerror", lambda exc: js_errors.append(str(exc)))
            for route in PAGES:
                name = f"UX-01-{width}-{route}"

                def run_layout(route=route, width=width):
                    page.goto(HTML.as_uri() + "#" + route, wait_until="load")
                    page.wait_for_selector("#main", state="attached")
                    measurements = page.evaluate("""() => ({
                        documentWidth: document.documentElement.scrollWidth,
                        bodyWidth: document.body.scrollWidth,
                        innerWidth: window.innerWidth,
                        main: document.querySelector('#main')?.textContent?.length || 0
                    })""")
                    if measurements["main"] < 80:
                        raise AssertionError(f"main content missing for {route}: {measurements}")
                    if route in SCREENSHOT_PAGES:
                        page.screenshot(path=str(OUTPUT / f"{route}-{width}.png"), full_page=True, animations="disabled")
                    if measurements["documentWidth"] > measurements["innerWidth"] + 2:
                        offenders = page.evaluate("""() => [...document.querySelectorAll('body *')]
                          .map(el => ({el, rect: el.getBoundingClientRect(), css: getComputedStyle(el)}))
                          .filter(x => x.rect.right > innerWidth + 2 && x.rect.width > 0 &&
                            x.css.position !== 'fixed')
                          .sort((a,b) => b.rect.right - a.rect.right)
                          .slice(0,15)
                          .map(x => ({
                            tag:x.el.tagName, cls:String(x.el.className).slice(0,90),
                            width:Math.round(x.rect.width), right:Math.round(x.rect.right),
                            overflow:x.css.overflowX,
                            html:x.el.outerHTML.slice(0,140)
                          }))""")
                        raise AssertionError(f"unintended page-wide horizontal overflow: {measurements}; offenders={offenders}")
                    return measurements

                check(name, run_layout)
            check(f"UX-09-{width}-js-errors", lambda: (
                (_ for _ in ()).throw(AssertionError(str(js_errors))) if js_errors else {"errors": 0}
            ))
            if width == 390:
                def focus_and_skip():
                    page.goto(HTML.as_uri() + "#home")
                    page.keyboard.press("Tab")
                    focus = page.evaluate("document.activeElement?.outerHTML || ''")
                    if "v8Skip" not in focus and "跳到主要工作区" not in focus:
                        raise AssertionError("first Tab did not reach skip-to-main link: " + focus[:150])
                    return {"first_tab": focus[:150]}
                check("UX-06-390-skip-link", focus_and_skip)

                def ai_panel():
                    page.goto(HTML.as_uri() + "#home")
                    page.locator("details.v81Other > summary").click()
                    page.locator('button[data-v8-mode="assist"]').click()
                    page.locator('button[data-v8-next="assist-desk"]').click()
                    panel = page.locator("#v81-evidence")
                    panel.wait_for(state="visible")
                    if not page.evaluate("document.activeElement?.id === 'v81-evidence'"):
                        raise AssertionError("AI evidence panel not focused")
                    expanded = page.locator('button[data-v8-next="assist-desk"]').get_attribute("aria-expanded")
                    if expanded != "true":
                        raise AssertionError("aria-expanded not true")
                    page.screenshot(path=str(OUTPUT / "ai-evidence-390.png"), full_page=True)
                    page.locator('button[data-v81-close-evidence="1"]').click()
                    if panel.count():
                        raise AssertionError("evidence panel not closed")
                    return {"expanded": expanded, "focused": True, "closed": True}
                check("UX-06-390-ai-evidence-focus", ai_panel)

                def feedback():
                    page.goto(HTML.as_uri() + "#product")
                    page.locator('button[data-v81-productissue="1"]').click()
                    main_text = page.locator("#main").inner_text()
                    if "DATASET:DEMO-DS-901" not in main_text or "DEMO-DV-3" not in main_text:
                        raise AssertionError("precise Dataset source/version missing in new feedback")
                    page.screenshot(path=str(OUTPUT / "feedback-new-dataset-390.png"), full_page=True)
                    return {"source": "DATASET:DEMO-DS-901", "version": "DEMO-DV-3"}
                check("UX-05-390-feedback-source", feedback)

            context.close()
        browser.close()

    passed = sum(x["status"] == "PASS" for x in evidence["checks"])
    failed = len(evidence["checks"]) - passed
    evidence["summary"] = {"passed": passed, "failed": failed, "total": len(evidence["checks"])}
    (OUTPUT / "result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUTPUT / 'result.json'}: {passed} PASS, {failed} FAIL")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
