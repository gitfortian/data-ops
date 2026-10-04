from pathlib import Path
b=Path('data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src')
main=b/'main/java/io/yak/ops/business/sync/offline'
po=(main/'dao/model/OfflineJobRevisionPO.java').read_text(encoding='utf-8')
domain=po.replace('sync.offline.dao.model;', 'sync.offline.domain;').replace('import com.baomidou.mybatisplus.annotation.IdType;\n','').replace('import com.baomidou.mybatisplus.annotation.TableId;\n','').replace('import com.baomidou.mybatisplus.annotation.TableName;\n','').replace('@TableName("yak_offline_job_revision")\n','').replace('  @TableId(type = IdType.AUTO)\n','').replace('OfflineJobRevisionPO','OfflineJobRevision').replace('离线同步任务发布版本快照（append-only，W1-2 契约 C2）。','离线同步任务发布版本快照；持久化只允许追加，不含 ORM 契约。')
(main/'domain/OfflineJobRevision.java').write_text(domain,encoding='utf-8')
for p in list((main/'definition').glob('*.java'))+[main/'repository/OfflineJobRevisionRepository.java']+list((b/'test').rglob('*.java')):
 if 'repository' in p.parts and p.name!='OfflineJobRevisionRepository.java':continue
 s=p.read_text(encoding='utf-8').replace('sync.offline.dao.model.OfflineJobRevisionPO','sync.offline.domain.OfflineJobRevision').replace('OfflineJobRevisionPO','OfflineJobRevision')
 p.write_text(s,encoding='utf-8')
p=main/'repository/OfflineJobRevisionRepositoryAdapter.java';s=p.read_text(encoding='utf-8').replace('import java.util.Collection;', 'import io.yak.ops.business.sync.offline.domain.OfflineJobRevision;\nimport org.springframework.beans.BeanUtils;\nimport java.util.Collection;')
s=s.replace('List<OfflineJobRevisionPO> find','List<OfflineJobRevision> find').replace('Optional<OfflineJobRevisionPO> find','Optional<OfflineJobRevision> find').replace('public OfflineJobRevisionPO insert(OfflineJobRevisionPO revision)', 'public OfflineJobRevision insert(OfflineJobRevision revision)')
s=s.replace('.orderByDesc(OfflineJobRevisionPO::getVersionNo));','.orderByDesc(OfflineJobRevisionPO::getVersionNo)).stream().map(this::toDomain).toList();')
s=s.replace('.eq(OfflineJobRevisionPO::getVersionNo, versionNo)));','.eq(OfflineJobRevisionPO::getVersionNo, versionNo))).map(this::toDomain);')
s=s.replace('Optional.ofNullable(revisionMapper.selectById(revisionId));','Optional.ofNullable(revisionMapper.selectById(revisionId)).map(this::toDomain);')
s=s.replace('revisionMapper.selectBatchIds(latestRowIds);','revisionMapper.selectBatchIds(latestRowIds).stream().map(this::toDomain).toList();')
s=s.replace('.last("LIMIT 1")));','.last("LIMIT 1"))).map(this::toDomain);')
s=s.replace('    revisionMapper.insert(revision);','    OfflineJobRevisionPO row = new OfflineJobRevisionPO();\n    BeanUtils.copyProperties(revision, row);\n    revisionMapper.insert(row);\n    revision.setId(row.getId());\n    revision.setCreateTime(row.getCreateTime());')
s=s.replace('\n  @Override\n  public OfflineJobRevision insert', '''
  private OfflineJobRevision toDomain(OfflineJobRevisionPO row) {
    OfflineJobRevision revision = new OfflineJobRevision();
    BeanUtils.copyProperties(row, revision);
    return revision;
  }

  @Override
  public OfflineJobRevision insert''');p.write_text(s,encoding='utf-8')
p=b/'test/java/io/yak/ops/business/sync/offline/controller/OfflineSyncContractTest.java';s=p.read_text(encoding='utf-8').replace('persistenceObjectsBelongToCommonModulePackage','persistenceObjectsBelongToOwningModulePackage').replace('io.yak.ops.common.bean.po.sync.offline','io.yak.ops.business.sync.offline.dao.model');p.write_text(s,encoding='utf-8')
