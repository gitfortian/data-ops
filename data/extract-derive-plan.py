from pathlib import Path
p=Path('data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/derive/ModelDeriveService.java')
s=p.read_text(encoding='utf-8')
start=s.index('  /**\n   * 内部:一条解析后的字段。')
end=s.index('  // ------------------------------------------------------------------ preview',start)
block=s[start:end]
record=block[block.index('  private record ResolvedField('):block.index('  /** 目标层技术列定义')]
record=record.replace('  private record ResolvedField(', 'record ResolvedDeriveField(').replace('ResolvedField','ResolvedDeriveField').replace('TechnicalDef','DeriveTechnicalColumn').replace('FIELD_ROLE_MEASURE.equals','"MEASURE".equals')
record='\n'.join(line[2:] if line.startswith('  ') else line for line in record.splitlines())+'\n'
imports='''package io.yak.ops.business.modeling.derive;

import io.yak.ops.business.modeling.governance.StandardFieldMatcher;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.modeling.derive.ModelDeriveService.AggregateSpec;

/** Immutable resolved inheritance facts shared by preview and derivation. */
'''
p.with_name('ResolvedDeriveField.java').write_text(imports+record,encoding='utf-8')
p.with_name('DeriveTechnicalColumn.java').write_text('''package io.yak.ops.business.modeling.derive;

/** A mandatory target-layer technical column owned by derivation rules. */
record DeriveTechnicalColumn(String name, String dataType, Integer length, String note) {}
''',encoding='utf-8')
s=s[:start]+s[end:]
s=s.replace('ResolvedField','ResolvedDeriveField').replace('TechnicalDef','DeriveTechnicalColumn')
s=s.replace('import java.util.regex.Matcher;\n','').replace('import java.util.regex.Pattern;\n','')
start=s.index('    Map<String, ResolvedDeriveField> inherited =',s.index('  private List<ResolvedDeriveField> mergeSelection('))
end=s.index('        Long stdFieldId =',start)
s=s[:start]+'''    DeriveFieldSelection.Plan plan = DeriveFieldSelection.plan(
        request.fields(), resolution.fields(), List.copyOf(TARGET_TECHNICALS.values()));
    List<ResolvedDeriveField> selection = new ArrayList<>();
    for (DeriveFieldSelection.Choice choice : plan.choices()) {
        DerivedField chosen = choice.chosen();
        ResolvedDeriveField base = choice.base();
'''+s[end:]
start=s.index('      }\n    }\n    Set<String> present =',s.index('  private List<ResolvedDeriveField> mergeSelection('))
end=s.index('      selection.add(',start)
s=s[:start]+'''    }
    for (DeriveTechnicalColumn technical : plan.missingTechnicals()) {
'''+s[end:]
p.write_text(s,encoding='utf-8')
