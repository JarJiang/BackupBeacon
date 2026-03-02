# BackupBeacon

BackupBeacon 是一个面向内网环境、基于 Docker 的数据库自动备份平台，目标是用尽量简单的方式完成“页面配置 + 自动备份”。

## 当前定位

- 单容器部署
- 页面可视化配置备份
- 自动定时执行备份
- 备份输出到服务器目录（支持共享磁盘挂载）

## 当前范围（v1）

- 数据库连接管理（MySQL/PostgreSQL）
- 备份策略管理（整库/指定表、全量/增量）
- 调度执行（按间隔）
- 任务记录与站内通知
- 备份目录可配置为服务器路径

当前不做：

- 恢复能力
- 外部通知（如 Telegram）
- 复杂多容器拆分

## 技术栈

- 后端：Java 8 + Spring Boot 2.7
- 前端：Vue2 + Element UI（静态资源内置）
- 配置存储：SQLite
- 部署：Docker Compose（单容器）

## JDK 版本锁定

项目锁定 **JDK 8（Temurin 8）**。

如果你本机有多版本 JDK（例如 8/17），请先切到 JDK 8：

```powershell
scoop reset temurin8-jdk
java -version
```

## 快速开始

```bash
# 本地先跑（推荐）
mvn spring-boot:run

# 或本地验证后再 Docker
# docker compose up -d --build
```

访问：`http://localhost:8080`

## 文档

- 启动说明：`SETUP.md`
- 产品需求：`docs/PRD.md`
- 架构说明：`docs/ARCHITECTURE.md`
- 路线图：`docs/ROADMAP.md`

## License

MIT
