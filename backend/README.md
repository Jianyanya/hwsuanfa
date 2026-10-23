# 后端

Spring Boot 3、Java 21、MyBatis-Plus、MySQL 8、Redis，默认端口 8080。

完整配置、初始化、新站主创建和上线更新步骤见 [根目录 README](../README.md)。

- `src/main/resources/db/schema.sql`：仅用于空库，包含删表语句。
- `src/main/resources/db/data.sql`：公共积分和难度规则，不包含账号或成员数据。
- `src/main/resources/db/migration-*.sql`：已有库结构迁移，执行前备份并核对适用版本。
- `application-local.yml`：本机私有配置，不提交、不打包。

从本目录执行 `mvn spring-boot:run` 启动，`mvn test` 测试，`mvn clean package` 打包。生产环境必须提供自己的数据库密码与 JWT 密钥。不存在发布用默认账号。

`/api/admin/**` 仅站主可访问；普通用户身份由登录令牌确定。洛谷元数据回填接口为 `POST /api/admin/problems/backfill-metadata`，提交导入接口为 `POST /api/submissions/import/LUOGU`。
