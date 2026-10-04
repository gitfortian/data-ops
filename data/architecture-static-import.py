from pathlib import Path
p=Path('scripts/architecture/import-boundaries.mjs');s=p.read_text(encoding='utf-8').replace("    return [...classes.entries()].filter", "    if (classes.has(name.slice(0, -2))) return [classes.get(name.slice(0, -2))];\n    return [...classes.entries()].filter");p.write_text(s,encoding='utf-8')
