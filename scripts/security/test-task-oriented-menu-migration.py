"""Execute V2045 twice on an isolated MySQL database and verify grant safety.

Requires mysql CLI and an account allowed to create a temporary database.
YAK_NAV_TEST_MYSQL_HOST/PORT/USER/PASSWORD configure the connection.
The existing application database is never selected or modified.
"""

import argparse
import os
from pathlib import Path
import re
import subprocess
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql-bin', default='mysql')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    migrations = root / 'data-ops-boot/src/main/resources/yak-security/db/migration'
    database = 'yak_nav_test_' + uuid.uuid4().hex
    assert re.fullmatch(r'yak_nav_test_[a-f0-9]{32}', database)
    app = 'navigation-test'
    env = dict(os.environ, MYSQL_PWD=os.environ.get('YAK_NAV_TEST_MYSQL_PASSWORD', ''))
    command = [args.mysql_bin, '--batch', '--raw', '--skip-column-names',
               '--default-character-set=utf8mb4', '--host=' + env.get('YAK_NAV_TEST_MYSQL_HOST', '127.0.0.1'),
               '--port=' + env.get('YAK_NAV_TEST_MYSQL_PORT', '3306'),
               '--user=' + env.get('YAK_NAV_TEST_MYSQL_USER', 'root')]

    def run(sql, selected=True):
        result = subprocess.run(command + ([database] if selected else []),
                                input=sql, text=True, encoding='utf-8',
                                capture_output=True, env=env, check=False)
        if result.returncode:
            # mysql reports SQL errors without including connection credentials.
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip().splitlines()

    def snapshot():
        return {table: run(f'SELECT * FROM {table} ORDER BY 1, 2;') for table in [
            'yak_security_menu', 'yak_security_permission',
            'yak_security_role_permission', 'yak_security_role_menu']}

    run(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4;', selected=False)
    try:
        run('''
CREATE TABLE yak_security_menu (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, menu_code VARCHAR(128), menu_name VARCHAR(128),
 parent_code VARCHAR(128), route_path VARCHAR(256), icon_key VARCHAR(64), menu_type INT,
 sort_order INT, visible INT, active INT, required_permission_code VARCHAR(128),
 description VARCHAR(512), app_name VARCHAR(128), is_delete INT DEFAULT 0,
 UNIQUE KEY uk_menu (app_name, menu_code));
CREATE TABLE yak_security_permission (
 id BIGINT PRIMARY KEY, permission_code VARCHAR(128), app_name VARCHAR(128),
 active INT DEFAULT 1, is_delete INT DEFAULT 0);
CREATE TABLE yak_security_role_permission (
 role_id BIGINT, permission_id BIGINT, app_name VARCHAR(128), is_delete INT DEFAULT 0,
 PRIMARY KEY (role_id, permission_id, app_name));
CREATE TABLE yak_security_role_menu (
 role_id BIGINT, menu_id BIGINT, app_name VARCHAR(128), is_delete INT DEFAULT 0,
 PRIMARY KEY (role_id, menu_id, app_name));
''')
        # Seed the real preceding catalog values, including retired containers.
        pattern = re.compile(r"\(\s*'([^']+)'\s*,\s*'[^']*'\s*,\s*(?:NULL|'[^']*')\s*,\s*(?:NULL|'[^']*')\s*,\s*'[^']*'\s*,\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*(?:NULL|'[^']*')\s*,\s*'[^']*'\s*,\s*'\$\{appName\}'\s*\)")
        rows = {}
        for file in sorted(migrations.glob('V*.sql'), key=lambda p: int(p.name.split('__')[0][1:])):
            version = int(file.name.split('__')[0][1:])
            if not 2006 <= version < 2042:
                continue
            for match in pattern.finditer(file.read_text(encoding='utf-8')):
                rows[match[1]] = match[0]
        assert rows and 'data-asset-catalog' in rows
        columns = 'menu_code,menu_name,parent_code,route_path,icon_key,menu_type,sort_order,visible,active,required_permission_code,description,app_name'
        for application in [app, 'other-application']:
            run(f'INSERT INTO yak_security_menu ({columns}) VALUES ' +
                ','.join(row.replace('${appName}', application) for row in rows.values()) + ';')
        run(f'''INSERT INTO yak_security_menu ({columns}) VALUES
 ('system','系统管理',NULL,NULL,'system',1,90,1,1,NULL,'框架目录','{app}'),
 ('system-security-projects','项目空间','system','/system/projects','system',2,40,1,1,'security:project:read','框架页面','{app}');
INSERT INTO yak_security_permission (id, permission_code, app_name, active) VALUES
 (1,'data-asset:read','{app}',1),(2,'security:root','{app}',1),
 (3,'data-asset:read','{app}',0),(4,'data-asset:create','{app}',1),
 (5,'data-asset:read','other-application',1);
INSERT INTO yak_security_role_permission (role_id,permission_id,app_name,is_delete) VALUES
 (3,1,'{app}',0),(4,2,'{app}',0),(8,1,'{app}',1),(9,3,'{app}',0),
 (10,4,'{app}',0),(20,5,'other-application',0);''')
        for role, code, deleted in [(1, 'data-quality-table-config', 0), (2, 'data-asset-catalog', 0),
                                    (5, 'data-approval-todo', 0), (6, 'data-approval-flows', 0),
                                    (7, 'resource-management', 0), (11, 'data-asset-catalog', 1)]:
            run(f"INSERT INTO yak_security_role_menu SELECT {role},id,app_name,{deleted} FROM yak_security_menu WHERE app_name='{app}' AND menu_code='{code}';")
        before = snapshot()
        prior_leaves = run(f"SELECT id,menu_code,route_path,COALESCE(required_permission_code,'NULL') FROM yak_security_menu WHERE app_name='{app}' AND menu_type=2 AND active=1 ORDER BY id;")
        sql = (migrations / 'V2045__task_oriented_navigation.sql').read_text(encoding='utf-8').replace('${appName}', app)
        run(sql)
        first = snapshot()
        run(sql)
        assert snapshot() == first, 'Migration must be idempotent'
        assert set(before['yak_security_role_menu']).issubset(first['yak_security_role_menu']), 'Existing grants were removed or changed'
        for table in ['yak_security_permission', 'yak_security_role_permission']:
            assert first[table] == before[table], 'Migration changed action permissions'
        assert [row for row in first['yak_security_menu'] if '\tother-application\t' in row] == [
            row for row in before['yak_security_menu'] if '\tother-application\t' in row], 'Cross-application catalog mutation'
        after_leaves = run(f"SELECT id,menu_code,route_path,COALESCE(required_permission_code,'NULL') FROM yak_security_menu WHERE app_name='{app}' AND menu_type=2 AND active=1 AND menu_code<>'consumption-catalog' ORDER BY id;")
        assert prior_leaves == after_leaves, ('Existing leaf IDs, URLs or permission requirements changed: '
            f'before-only={set(prior_leaves) - set(after_leaves)}, after-only={set(after_leaves) - set(prior_leaves)}')
        grants = run(f"SELECT rm.role_id,m.menu_code FROM yak_security_role_menu rm JOIN yak_security_menu m ON m.id=rm.menu_id AND m.app_name=rm.app_name WHERE rm.app_name='{app}' AND rm.is_delete=0 ORDER BY 1,2;")
        pairs = {tuple(row.split('\t')) for row in grants}
        assert {role for role, code in pairs if code == 'consumption-catalog'} == {'2', '3', '4'}, 'Discovery grant widened or eligible users lost access'
        for role, code in [('1', 'data-quality'), ('1', 'data-asset'), ('2', 'data-analysis'),
                           ('6', 'system'), ('7', 'integration')]:
            assert (role, code) in pairs, 'Missing new parent grant'
        assert ('5', 'data-approval-flows') not in pairs and ('6', 'data-approval-todo') not in pairs
        new_leaf_pairs = run(f"SELECT rm.role_id,m.menu_code FROM yak_security_role_menu rm JOIN yak_security_menu m ON m.id=rm.menu_id AND m.app_name=rm.app_name WHERE rm.app_name='{app}' AND rm.is_delete=0 AND m.menu_type=2 ORDER BY 1,2;")
        assert set(new_leaf_pairs) == {'1\tdata-quality-table-config', '2\tdata-asset-catalog',
            '2\tconsumption-catalog', '3\tconsumption-catalog', '4\tconsumption-catalog',
            '5\tdata-approval-todo', '6\tdata-approval-flows', '7\tresource-management'}, 'Sibling page privileges added'
        assert run(f"SELECT menu_code FROM yak_security_menu WHERE app_name='{app}' AND menu_type=1 AND parent_code IS NULL AND active=1 AND menu_code<>'system' ORDER BY sort_order;") == [
            'integration', 'modeling', 'development', 'data-asset', 'data-analysis']
        print('PASS: MySQL V2045 replay, idempotence, leaf identity, role preservation, least privilege and app isolation')
    finally:
        run(f'DROP DATABASE `{database}`;', selected=False)
        print('Temporary migration database removed.')


if __name__ == '__main__':
    main()
