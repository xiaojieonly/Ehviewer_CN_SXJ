# 发行版打包（包管理器预适配）

## 当前分发：zip（官方 Release 产物）

`scripts/package.sh` 产出官方发布 zip：

```
anotherviewer-<version>-<os>-<arch>.zip
├── lib/app.jar
├── bin/start.sh / bin/stop.sh
├── data/            （预建固定结构）
└── README.txt
```

zip 是发布的主力形式：跨平台（Linux/macOS）、解压即用、无包管理器依赖。

## 包管理器预适配架构

为未来 deb/rpm 包预留的架构骨架，核心是**路径 ↔ 抽象的一一对应**，两套分发共用同一
`--data-dir` 抽象，无逻辑分叉：

| zip 发布包（scripts/package.sh） | deb/rpm 包（packaging/ospackage.gradle） | 抽象 |
| --- | --- | --- |
| `lib/app.jar` | 安装到 `/opt/anotherviewer/lib/app.jar` | 同一 app.jar 可执行 fat jar |
| `data/`（解压目录内） | `/var/lib/anotherviewer` | 同一数据目录语义（`ANOTHERVIEWER_DATA_DIR` 环境变量 / Spring `anotherviewer.data-dir`） |
| `bin/start.sh`（脚本内推导） | `anotherviewer.service`（systemd unit） | 同一启动语义：`java -jar lib/app.jar` + `ANOTHERVIEWER_DATA_DIR` 注入 |

数据目录固定结构在两处声明一致：`anotherviewer.db` / `security.key` / `downloads/` / `cache/` /
`backups/`（zip 内 `data/README.md` 与 unit 文件均依赖该结构）。

## 激活 ospackage

默认不激活，不影响现有构建。需要产出 deb/rpm 时：

1. 在 `anotherviewer-web/build.gradle.kts` 的 plugins 块加入 nebula.ospackage
   （Groovy 脚本经 `apply(from = rootProject.file("packaging/ospackage.gradle"))` 接入）；
2. 构建 jar 后执行：

   ```bash
   ./gradlew :anotherviewer-web:bootJar :anotherviewer-web:buildDeb :anotherviewer-web:buildRpm
   ```

3. 按目标发行版调整 `packaging/ospackage.gradle` 中的依赖声明
   （`openjdk-21-jre-headless` 为 Debian 系名称，RPM 系需映射为 `java-21-openjdk-headless`）。

## systemd unit 模板

`packaging/systemd/anotherviewer.service.tpl` 为 systemd 安装模板：用户 `anotherviewer`、
`WorkingDirectory=/opt/anotherviewer`、数据目录 `/var/lib/anotherviewer`。
