# 项目工具

## 洛谷同步

`hwsuanfa-luogu-sync.user.js` 为 Tampermonkey 脚本。部署时修改 `SITE_BASE`，在本站个人中心绑定洛谷账号，在洛谷保持登录并设置本站 Access Token；令牌失效后重新设置，不把令牌写进脚本源码。

“同步 AC 记录”分页读取洛谷记录并上传。重复提交按现有服务端去重规则处理。

“回填题目标签”调用站主接口，普通成员没有该权限。当前实现仍由服务端逐题请求洛谷，受权限和反爬限制；页面解析兼容性及批处理耗时也影响结果，不能把浏览器解析测试通过视为后台回填实测通过。此版本尚未实现 GoAlgo 式完整会话、增量检查点及共享元数据队列。

## 可重复检查

- `node --test tools/test-luogu-metadata.cjs`：离线解析测试，不访问用户账号。
- `node tools/check-schema.cjs`：实体与 SQL 字段一致性检查。
- `node tools/check-release.cjs`：检查发布清单、敏感文件及明显凭据模式，不输出凭据值。
- `node tools/verify-fresh-schema.cjs`：需设置 `DB_PASSWORD`，可设置 `DB_USERNAME`；本机 MySQL 客户端需可用。创建独立临时数据库验证建库，检查不存在默认账号、示例队伍或公告，结束时清理临时库。

旧的真实账号验收脚本、浏览器报告和截图不随源码发布，已留在本机忽略目录；它们可能操作业务记录，不用于生产库清理。单元测试代码仍保留在前后端测试目录。
