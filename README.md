# GTO Publish Plugin

可复用的多目标发布 Gradle 插件，支持 Maven、GitHub Release、CurseForge。

> **项目地址 / Repository**: https://github.com/GregTech-Odyssey/GradlePublishPlugin

## 前置要求 / Prerequisites

消费项目必须满足以下条件才能使用此插件：

1. **应用 `maven-publish` 插件** 并配置 Maven 仓库（名称需与 `gtoPublish.mavenRepoName` 一致）：

```groovy
plugins {
    id 'maven-publish'
}

publishing {
    repositories {
        maven {
            name = 'gtodysseyRepository'   // 必须与 gtoPublish.mavenRepoName 一致
            url = 'https://maven.gtodyssey.com/releases'
            credentials {
                username = findProperty('gtodysseyRepositoryUsername') ?: ''
                password = findProperty('gtodysseyRepositoryPassword') ?: ''
            }
        }
    }
    publications {
        mavenJava(MavenPublication) {
            from components.java
        }
    }
}
```

2. **配置凭证**（见下方 [凭证配置](#凭证配置) 章节）

3. **版本号格式**需符合 `x.x.x[-alpha|-beta|-release]`（见下方 [版本格式](#版本格式) 章节）

## 安装

在消费项目的 `settings.gradle` 中配置 pluginManagement：

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven { url 'https://maven.gtodyssey.com/releases' }
        gradlePluginPortal()
    }
}

// build.gradle
plugins {
    id 'com.gto.gtopublishgradleplugin' version '1.0.0'
}
```

## 版本格式

项目版本必须符合 `x.x.x[-alpha|-beta|-release]` 格式：

- `x.x.x`：三段数字版本号
- 发布类型：`-alpha`、`-beta`、`-release` 或省略（省略等同于 `-release`）

| 版本号示例 | 实际发布版本 | 类型 | GitHub 行为 | CurseForge `releaseType` |
|---|---|---|---|---|
| `1.0.0-alpha` | `1.0.0-alpha` | Alpha | Pre-release，名称前缀 `[Alpha]` | `alpha` |
| `1.0.0-beta` | `1.0.0-beta` | Beta | Pre-release，名称前缀 `[Beta]` | `beta` |
| `1.0.0-release` | `1.0.0` | Release | 正式发布 | `release` |
| `1.0.0` | `1.0.0` | Release | 正式发布 | `release` |

> **注意**：`-release` 后缀会自动从所有发布产物的文件名和版本号中去除。

## 配置

### 方式一：通过 `gtoPublish` 扩展块（推荐）

```groovy
gtoPublish {
    publishMaven      = true        // 默认 true
    publishGithub     = false       // 默认 false
    publishCurseforge = false       // 默认 false
    mavenRepoName     = 'gtodysseyRepository'  // 默认值
    mavenRepoUrl      = 'https://maven.gtodyssey.com/releases'  // 默认值
    githubRepo        = 'owner/repo-name'      // GitHub 仓库（启用 GitHub 发布时必填）
    curseforgeProjectId   = '123456'    // CurseForge 项目 ID（启用 CurseForge 时必填）
    minecraftVersion      = '26.1'      // Minecraft 版本（默认 26.1）
    curseforgeModLoader   = 'NeoForge'  // 模组加载器（启用 CurseForge 时必填，如 NeoForge, Forge, Fabric）
    curseforgeJavaVersion = 'Java 25'   // Java 版本（启用 CurseForge 时必填，如 Java 8, Java 17, Java 21, Java 25）
}
```

### 方式二：通过 `gradle.properties` 覆盖

所有扩展属性都可通过项目级或全局 `gradle.properties` 覆盖：

| 扩展属性 | gradle.properties Key |
|---|---|
| `publishMaven` | `gtoPublishMaven` |
| `publishGithub` | `gtoPublishGithub` |
| `publishCurseforge` | `gtoPublishCurseforge` |
| `mavenRepoName` | `gtoMavenRepoName` |
| `mavenRepoUrl` | `gtoMavenRepoUrl` |
| `githubRepo` | `gtoGithubRepo` |
| `curseforgeProjectId` | `gtoCurseforgeProjectId` |
| `minecraftVersion` | `gtoCurseforgeMinecraftVersion` |
| `curseforgeModLoader` | `gtoCurseforgeModLoader` |
| `curseforgeJavaVersion` | `gtoCurseforgeJavaVersion` |

## 凭证配置

在 `~/.gradle/gradle.properties` 中设置（**不要**提交到项目中）：

```cmd
:: Windows CMD 一键打开（自动创建目录和文件）
(if not exist "%USERPROFILE%\.gradle" mkdir "%USERPROFILE%\.gradle") & (if not exist "%USERPROFILE%\.gradle\gradle.properties" type nul > "%USERPROFILE%\.gradle\gradle.properties") & notepad "%USERPROFILE%\.gradle\gradle.properties"
```

```bash
# macOS / Linux
mkdir -p ~/.gradle && touch ~/.gradle/gradle.properties && nano ~/.gradle/gradle.properties
```

```properties
# Maven 仓库凭证（联系 xinxinsuried 获取）
gtodysseyRepositoryUsername=你的用户名
gtodysseyRepositoryPassword=你的密码

# GitHub Token
# 获取方式: GitHub → Settings → Developer settings → Personal access tokens → Generate new token
# 需要权限: repo (Full control of private repositories)
gtoGithubToken=ghp_xxxxxxxxxxxx

# CurseForge (如启用)
# 获取方式: https://www.curseforge.com/account/api-tokens
gtoCurseforgeToken=你的API密钥
```

GitHub Token 也支持环境变量 `GH_TOKEN` 或 `GITHUB_TOKEN`。

## 使用

```bash
# 完整发布流程（推荐）
./gradlew gtoPublish

# 仅校验凭证和版本
./gradlew gtoValidate

# 检查 Maven 版本是否已存在
./gradlew gtoCheckMavenVersion

# 单独发布到某个目标
./gradlew gtoPublishMaven
./gradlew gtoPublishGithub
./gradlew gtoPublishCurseforge
```

> **推荐发布顺序**：Maven → GitHub → CurseForge。GitHub 和 CurseForge 发布前会自动校验本地 JAR 与 Maven 仓库中产物的 SHA-1 一致性，因此 Maven 必须先完成发布。
>
> **一键发布说明**：使用 `gtoPublish` 且同时启用了 Maven 时，GitHub 和 CurseForge 任务会自动跳过 Maven SHA-1 一致性校验，因为 Maven 制品已在同一次构建中发布，而部分构建工具（如 NeoForge）会产生非确定性 JAR，重新构建后 SHA-1 会不同。单独执行 `gtoPublishGithub` 或 `gtoPublishCurseforge` 时仍会进行校验。

## 注册的 Task

| Task | 描述 |
|---|---|
| `gtoValidate` | 校验凭证配置和版本号是否冲突 |
| `gtoCheckMavenVersion` | 检查版本在 Maven 仓库中是否已存在 |
| `gtoPublishMaven` | 发布到 Maven 仓库（委托给内置 publish） |
| `gtoPublishGithub` | 创建 GitHub Release 并上传 JAR（包含 Maven 一致性校验） |
| `gtoPublishCurseforge` | 上传 JAR 到 CurseForge（包含 Maven 一致性校验） |
| `gtoPublish` | 总入口：validate → build → 所有启用目标 |

## 发布此插件

插件自身版本通过 `gradle.properties` 中的 `gto_plugin_version` 配置，同样遵循版本格式规则：

```properties
gto_plugin_version=1.0.0-release
```

```bash
./gradlew publish
```

需要在 `~/.gradle/gradle.properties` 中配置 `gtodysseyRepositoryUsername` 和 `gtodysseyRepositoryPassword`。
