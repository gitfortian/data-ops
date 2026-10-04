from pathlib import Path
base=Path('data-ops-ui/src/pages')
def edit(p,f):
 s=p.read_text(encoding='utf-8');p.write_text(f(s),encoding='utf-8',newline='')
p=base/'development/data-development/components/data-service/DataServiceNodeEditor.tsx'
def service(s):
 s=s.replace('  const metadataContext = useSqlMetadataContext(node.id);','  const beginPublication = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);\n  const metadataContext = useSqlMetadataContext(node.id);')
 start=s.index('  const loadPublicationState =');end=s.index('\n  const load =',start)
 chunk=s[start:end].replace('  ) => {','  ) => {\n    const isCurrent = beginPublication();',1).replace('      setPublicationState(await fetchDataServicePublicationState(node.id));','      const state = await fetchDataServicePublicationState(node.id);\n      if (isCurrent()) setPublicationState(state);').replace('    } catch (error) {','    } catch (error) {\n      if (!isCurrent()) return;').replace('      setPublicationLoading(false);','      if (isCurrent()) setPublicationLoading(false);').replace('}, [node.id]);','}, [node.id, currentProject?.id, beginPublication]);')
 return s[:start]+chunk+s[end:]
edit(p,service)
p=base/'development/data-development/components/dataset/DatasetNodeEditor.tsx'
def dataset(s):
 start=s.index('  useEffect(() => {\n    listPublishedMetrics()');end=s.index('\n  useEffect',start+5)
 chunk=s[start:end].replace('    listPublishedMetrics()','    let active = true;\n    setMetricOptions([]);\n    setPublishedMetricsState(\'LOADING\');\n    listPublishedMetrics()').replace('.then((publications) => {','.then((publications) => {\n        if (!active) return;').replace('.catch((error) => {','.catch((error) => {\n        if (!active) return;').replace('  }, []);','    return () => { active = false; };\n  }, [currentProject?.id]);')
 return s[:start]+chunk+s[end:]
edit(p,dataset)
p=base/'data-analysis/lineage/LineageWorkspace.tsx'
def lineage(s):
 s="import { useSecurityProject } from '@/contexts/SecurityProjectContext';\nimport { useLatestOperation } from '@/hooks/useLatestOperation';\n"+s
 s=s.replace('export default function LineagePage() {','''export default function LineagePage() {
  const { currentProject } = useSecurityProject();
  const beginRootLoad = useLatestOperation(currentProject?.id);
  const beginSearch = useLatestOperation(currentProject?.id);
''')
 start=s.index('  const loadAssetByKey =');end=s.index('\n  useEffect',start)
 c=s[start:end].replace('    setLoading(true);','    const isCurrent = beginRootLoad();\n    setLoading(true);').replace('      selectRoot(asset, syncUrl);','      if (isCurrent()) selectRoot(asset, syncUrl);').replace('    } catch (error) {','    } catch (error) {\n      if (!isCurrent()) return;').replace('      setLoading(false);','      if (isCurrent()) setLoading(false);').replace('[selectRoot]','[selectRoot, beginRootLoad, currentProject?.id]')
 s=s[:start]+c+s[end:]
 # Project changes clear owned views; graph's cleanup also invalidates previous request.
 s=s.replace('  useEffect(() => {\n    // URL', '''  useEffect(() => {
    setRootAsset(undefined);
    setGraph(undefined);
    setSelectedAsset(undefined);
    setSelectedRelation(undefined);
    setSearchResults([]);
    setHasSearched(false);
    setSearching(false);
  }, [currentProject?.id]);

  useEffect(() => {
    // URL''').replace('[depth, direction, rootAsset]','[depth, direction, rootAsset, currentProject?.id]')
 start=s.index('  const runAssetSearch =');end=s.index('\n  const view =',start)
 c=s[start:end].replace('    const keyword =','    const isCurrent = beginSearch();\n    const keyword =').replace('      setSearchResults(values);','      if (isCurrent()) setSearchResults(values);').replace('    } catch {\n      setSearchResults([]);','    } catch {\n      if (isCurrent()) setSearchResults([]);').replace('      setSearching(false);','      if (isCurrent()) setSearching(false);').replace('[searchKeyword, searchType]','[searchKeyword, searchType, beginSearch, currentProject?.id]')
 return s[:start]+c+s[end:]
edit(p,lineage)
p=base/'modeling/detail.tsx'
def model(s):
 s=s.replace('  const [highlightRowKey, setHighlightRowKey]', '  const editorRootRef = useRef<HTMLDivElement>(null);\n  const highlightTimerRef = useRef<ReturnType<typeof setTimeout>>();\n  useEffect(() => () => { clearTimeout(highlightTimerRef.current); }, []);\n  const [highlightRowKey, setHighlightRowKey]')
 s=s.replace('document.querySelector(`tr[data-row-key="${row.key}"]`)','editorRootRef.current?.querySelector(`tr[data-row-key="${row.key}"]`)').replace('    window.setTimeout(() => {\n      setHighlightRowKey(undefined);','    clearTimeout(highlightTimerRef.current);\n    highlightTimerRef.current = setTimeout(() => {\n      setHighlightRowKey(undefined);')
 s=s.replace('<RowInteractionContext.Provider value={{ highlight: highlightRowKey, dragging: draggingKeys }}>','<div ref={editorRootRef}>\n              <RowInteractionContext.Provider value={{ highlight: highlightRowKey, dragging: draggingKeys }}>').replace('</RowInteractionContext.Provider>','</RowInteractionContext.Provider>\n              </div>')
 return s
edit(p,model)
