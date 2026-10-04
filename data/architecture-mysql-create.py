import pymysql,os,json
from pathlib import Path
name='architecture_validation_20261003'
c=pymysql.connect(host='127.0.0.1',port=3306,user=os.environ.get('YAK_DATABASE_USERNAME','root'),password=os.environ.get('YAK_DATABASE_PASSWORD','123456'))
with c.cursor() as q:
 q.execute('CREATE DATABASE '+name+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
Path('data/architecture-mysql-test.json').write_text(json.dumps({'database':name}),encoding='utf-8')
c.close()
print('Created isolated architecture test schema.')
