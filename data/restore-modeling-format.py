from pathlib import Path
import subprocess
p=Path('data-ops-ui/src/pages/modeling/detail.tsx')
s=p.read_text(encoding='utf-8')
start=s.index('  useEffect(() => {\n    resetDraft();')
end=s.index('\n\n  useEffect(() => {\n    void loadStructure();',start)
reset=s[start:end]
p.write_text(subprocess.check_output(['git','show','HEAD:data-ops-ui/src/pages/modeling/detail.tsx']).decode('utf-8'),encoding='utf-8')
subprocess.run(['node','data/guard-modeling-operations.cjs'],check=True)
s=p.read_text(encoding='utf-8')
s=s.replace('  const { tableName,', '  const { resetDraft, tableName,',1)
s=s.replace('  useEffect(() => {\n    void loadStructure();',reset+'\n\n  useEffect(() => {\n    void loadStructure();',1)
s=s.replace('[modelId, publishApproval?.id, publishApproval?.status]', '[modelId, publishApproval?.id, publishApproval?.status, captureEditorResource]')
s=s.replace('  const bindStandardField = (row: ColumnDraft, code: string) => {','  const bindStandardField = (row: ColumnDraft, code: string) => {\n    clearTimeout(standardFieldSearchTimerRef.current);\n    beginStandardSearch();')
p.write_text(s,encoding='utf-8')
