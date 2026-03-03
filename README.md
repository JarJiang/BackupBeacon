# BackupBeacon

BackupBeacon 是一个面向内网环境的数据库自动备份平台，采用单容器 Docker 部署，目标是用最简单的方式完成：

- 页面配置数据库连接
- 自动/手动触发备份
- 查看任务记录与站内通知

## 当前定位（v1）

- 单容器部署（Spring Boot + SQLite + 静态 Vue2 页面）
- 仅做备份，不做恢复
- 通知仅站内，不接 Telegram/Webhook
- 备份目录使用服务器视角路径（支持共享磁盘挂载）

## 核心功能

- 连接管理：新增/删除/启停/立即执行，支持连接描述
- 连接管理采用列表主视图 + 抽屉新增，默认不展开大表单
- 连接校验：保存前校验数据库连通性
- 目录校验：校验备份目录可写
- 任务中心：默认展示近 24 小时未读任务，支持查看历史
- 通知中心：支持单条处理与一键已读
- 状态气泡：任务与通知页签支持未读计数气泡
- 连接列表：展示上次备份时间，便于快速判断健康状态

## 备份输出规则

默认输出路径格式：

`目标目录/连接名/YYYYMMDD/库名-HHmmss.sql`

## 运行要求

- Java 8（本地直接运行时）
- Docker / Docker Compose（推荐）
- 目标数据库客户端命令：
  - MySQL: `mysqldump`
  - PostgreSQL: `pg_dump`

说明：

- 如果 BackupBeacon 在 Docker 容器中运行，使用容器内客户端命令，不依赖宿主机 PATH。
- 如果 BackupBeacon 在本机进程运行，需要本机可执行 `mysqldump/pg_dump`（PATH 可见）。

## 快速开始

### 1) 本地运行（开发调试）

```bash
mvn spring-boot:run
```

默认访问：`http://localhost:18080`

### 2) Docker 运行（推荐）

```bash
docker compose up -d --build
```

启动后访问端口以 `docker-compose.yml` 映射为准。

## 配置说明

应用配置文件：`src/main/resources/application.yml`

当前关键配置：

- `server.port`: 应用监听端口
- `backupbeacon.fs.allowed-roots`: 目录选择器白名单根目录
- `BACKUPBEACON_PATH_DISPLAY_MAP`（规划中）: 容器路径到宿主机路径的展示映射（仅 UI 展示）

## 文档索引

- 启动说明：`SETUP.md`
- 产品需求：`docs/PRD.md`
- 架构说明：`docs/ARCHITECTURE.md`
- 路线图：`docs/ROADMAP.md`
- 改动清单：`docs/CHANGELOG.md`

## 安全说明

- 连接密码加密存储
- 首次启动需初始化密钥，默认写入 `./data/crypto.key`
- 可通过环境变量 `BACKUPBEACON_CRYPTO_KEY` 注入密钥（优先级更高）

## 许可证

MIT