# 数据库工具

## 本机 MySQL

- MySQL 8.4.11 免安装版放在 `C:\ZI\mysql\`（不在仓库里），配置 `C:\ZI\mysql\my.ini`：只监听 127.0.0.1:3306，utf8mb4，东八区。
- 库 `se_platform`；后端账号 `se_app` 只有这个库的权限。密码在 `C:\ZI\mysql\本机密码.txt` 和不提交的 `application-local.yml`。
- 后端用 MySQL：加 `mysql` 配置启动（`application-mysql.yml`），例如 `mvnw spring-boot:run -Dspring-boot.run.profiles=mysql`。
- 详细说明：`docs/提交材料/07-数据库说明（MySQL）.md`。

## 把 H2 开发库的数据搬到 MySQL（h2_to_mysql.py）

```powershell
# 1. 导出 H2（后端开着也可以）
java -cp $env:USERPROFILE\.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar org.h2.tools.Shell -url "jdbc:h2:file:C:/ZI/se-learning-platform/backend/data/platform;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;AUTO_SERVER=TRUE" -user sa -sql "SCRIPT SIMPLE COLUMNS TO 'h2dump.sql' CHARSET 'UTF-8'"
```

```powershell
# 2. MySQL 每张表有哪些列（先让后端用 mysql 配置启动一次，表就建好了）
mysql -u root -p -h 127.0.0.1 -N -e "SELECT table_name, GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='se_platform' GROUP BY table_name" > mysql_cols.tsv
```

```powershell
# 3. 转换，再导入（文件开头会清空要导入的表）
python tools/db/h2_to_mysql.py h2dump.sql mysql_cols.tsv h2_data_for_mysql.sql
```

```powershell
Get-Content h2_data_for_mysql.sql -Encoding UTF8 | mysql -u root -p -h 127.0.0.1 --default-character-set=utf8mb4 se_platform
```
