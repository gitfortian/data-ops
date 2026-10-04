from pathlib import Path
import re
root=Path.cwd()
for p in (root/'data-ops-business').rglob('*.md'):
 if p.name not in ('ARCHITECTURE.md','DEPENDENCIES.md'):continue
 s=p.read_text(encoding='utf-8');new=s.replace('`Boot `config.persistence.BusinessDatabaseConfiguration` 应用装配`','Boot 的 `config.persistence.BusinessDatabaseConfiguration` 应用装配')
 module=p.parent.name.removeprefix('data-ops-business-');domain='development' if module=='data-development' else module
 if domain in ('sync-offline','sync-realtime'):domain='sync.'+domain.removeprefix('sync-')
 new=new.replace(f'io.yak.ops.common.bean.po.{domain}',f'io.yak.ops.business.{domain}.dao.model').replace(f'bean.po.{domain}',f'business.{domain}.dao.model').replace(f'bean/po/{domain}',f'business/{domain}/dao/model')
 # Dependency rows must describe current ownership, not claim business PO is shared.
 if p.name=='DEPENDENCIES.md':
  new=re.sub(r'PO[（(]`?business\.'+re.escape(domain)+r'\.dao\.model`?[）)],?', '',new)
  new=new.replace('`BusinessDatabaseConfiguration`（共享数据源/开关）；','持久化开关注解；').replace('基础设施 `BusinessDatabaseConfiguration` + SPI','持久化开关注解 + SPI').replace('**仅基础设施**：`BusinessDatabaseConfiguration`（共享数据源/SqlSessionFactory/事务管理器）','现有持久化开关注解；共享数据库由 Boot 装配').replace('**仅基础设施**:`BusinessDatabaseConfiguration`(共享数据源/SqlSessionFactory/事务管理器)与 `ConditionalOnDataSourceEnabled`','`ConditionalOnDataSourceEnabled` 持久化条件').replace('共享业务数据源配置(`BusinessDatabaseConfiguration`/`DataSourceProperties`)与后续 catalog 元数据读取','现有持久化条件与 catalog 元数据读取')
  new=new.replace('-> datasource.config.BusinessDatabaseConfiguration','-> Boot config.persistence.BusinessDatabaseConfiguration (应用装配，无 business import)')
 if new!=s:p.write_text(new,encoding='utf-8')
# Replace explicit inaccurate ownership statements.
p=root/'data-ops-business/data-ops-business-modeling/ARCHITECTURE.md';s=p.read_text(encoding='utf-8');s=re.sub(r'持久化对象 `ModelingModelPO`[^\n]*','持久化对象归属本模块的 `dao.model`；权限码与错误码继续使用 `data-ops-common` 的稳定共享契约。',s);p.write_text(s,encoding='utf-8')
p=root/'data-ops-business/data-ops-business-semantic/ARCHITECTURE.md';s=p.read_text(encoding='utf-8');s=re.sub(r'- PO 位于[^\n]*','- PO 位于本模块 `io.yak.ops.business.semantic.dao.model`，只供本模块持久化适配器使用。',s);p.write_text(s,encoding='utf-8')
p=root/'data-ops-business/data-ops-business-lifecycle/ARCHITECTURE.md';s=p.read_text(encoding='utf-8');s=re.sub(r'- PO/权限码/错误码位于[^\n]*','- PO 位于本模块 `dao.model`；权限码/错误码保持 common 的稳定共享契约。',s);p.write_text(s,encoding='utf-8')
p=root/'data-ops-business/data-ops-business-dataset/ARCHITECTURE.md';s=p.read_text(encoding='utf-8');s=re.sub(r'它可以复用 Datasource 模块的[^\n]*','共享业务数据库、会话工厂与事务管理器由 Boot 装配；本模块只声明 Mapper/Flyway 和既有持久化条件，不导入 sibling 的数据库配置。',s);p.write_text(s,encoding='utf-8')
