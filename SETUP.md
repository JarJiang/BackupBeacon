# BackupBeacon 启动说明

## 一、JDK 版本锁定

当前项目锁定 **JDK 8**（Temurin 8）。

如果你使用 Scoop 安装了多版本 JDK，可这样切换：

```powershell
scoop reset temurin8-jdk
java -version
```

看到 `1.8` 再继续启动项目。

## 二、本地启动（推荐先本地验证）

### 方式 1：IDEA

- 用 IDEA 打开项目
- Project SDK 选择 JDK 8
- 运行 `BackupBeaconApplication`
- 访问 `http://localhost:8080`

### 方式 2：Maven 命令

```bash
mvn spring-boot:run
```

## 三、Docker 启动（本地验证通过后）

```bash
docker compose up -d --build
```

访问：`http://localhost:8080`

## 四、目录说明

- `./data`：系统配置数据库（SQLite）
- `./backup-target`：默认备份输出目录（可替换为服务器共享目录挂载）

## 五、共享目录挂载示例

把 `docker-compose.yml` 中：

- `./backup-target:/backup-target`

改成服务器实际共享挂载路径，例如：

- Linux：`/mnt/shared-backup:/backup-target`
- Windows Docker Desktop：`D:/shared-backup:/backup-target`

注意：页面里备份目录填写的是**容器内路径**（例如 `/backup-target`），不是操作者本机路径。
