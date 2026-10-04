from pathlib import Path
root=Path('data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src/main/java/io/yak/ops/business/sync/offline')
for p in root.rglob('*.java'):
 if 'dao' in p.relative_to(root).parts or p.name=='OfflineJobRevisionRepositoryAdapter.java':continue
 s=p.read_text(encoding='utf-8').replace('sync.offline.dao.model.OfflineJobRevisionPO','sync.offline.domain.OfflineJobRevision').replace('OfflineJobRevisionPO','OfflineJobRevision');p.write_text(s,encoding='utf-8')
