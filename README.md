# GTO Publish Plugin

可复用的多目标发布 Gradle 插件，支持 Maven、GitHub Release、CurseForge。

## 安装

在消费项目的 `build.gradle` 中：

```groovy
buildscript {
    repositories {
        maven { url 'https://maven.gtodyssey.com/releases' }
    }
    dependencies {
        classpath 'com.gto:gto-publish:1.0.0'
    }
}

apply plugin: 'com.gto.gto-publish'
```

或使用 `plugins` DSL（需要在 `settings.gradle` 中配置 pluginManagement）：

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
    id 'com.gto.gto-publish' version '1.0.0'
}
```

## 配置

### 方式一：通过 `gtoPublish` 扩展块（推荐）

```groovy
gtoPublish {
    publishMaven      = true        // 默认 true
    publishGithub     = true        // 默认 true
    publishCurseforge = false       // 默认 false
    mavenRepoName     = 'gtodysseyReleases'  // 默认值
    mavenRepoUrl      = 'https://maven.gtodyssey.com/releases'  // 默认值
    curseforgeGameVersions = ['26.1', 'NeoForge', 'Java 25']    // 默认值
}
```

### 方式二：通过 `gradle.properties`

```properties
gtoPublishMaven=true
gtoPublishGithub=true
gtoPublishCurseforge=false
gtoMavenRepoName=gtodysseyReleases
gtoMavenRepoUrl=https://maven.gtodyssey.com/releases
gtoCurseforgeGameVersions=26.1,NeoForge,Java 25
```

## 凭证配置

在 `~/.gradle/gradle.properties` 中设置（**不要**提交到项目中）：

```properties
# Maven 仓库凭证（联系 xinxinsuried 获取）
gtodysseyReleasesUsername=你的用户名
gtodysseyReleasesPassword=你的密码

# GitHub Token
# 获取方式: GitHub → Settings → Developer settings → Personal access tokens → Generate new token
# 需要权限: repo (Full control of private repositories)
gtoGithubToken=ghp_xxxxxxxxxxxx
gtoGithubRepo=owner/repo-name

# CurseForge (如启用)
# 获取方式: https://www.curseforge.com/account/api-tokens
gtoCurseforgeToken=你的API密钥
gtoCurseforgeProjectId=123456
```

GitHub Token 也支持环境变量 `GH_TOKEN` 或 `GITHUB_TOKEN`。

## 使用

```bash
# 完整发布流程（推荐）
./gradlew gtoPublish

# 仅校验凭证和版本
./gradlew gtoValidate

# 单独发布到某个目标
./gradlew gtoPublishMaven
./gradlew gtoPublishGithub
./gradlew gtoPublishCurseforge
```

## 注册的 Task

| Task                    | 描述                                     |
|-------------------------|------------------------------------------|
| `gtoValidate`           | 校验凭证配置和版本号是否冲突             |
| `gtoPublishMaven`       | 发布到 Maven 仓库（委托给内置 publish）  |
| `gtoPublishGithub`      | 创建 GitHub Release 并上传 JAR           |
| `gtoPublishCurseforge`  | 上传 JAR 到 CurseForge                   |
| `gtoPublish`            | 总入口：validate → build → 所有启用目标  |

## 发布此插件

```bash
./gradlew publish
```

需要在 `~/.gradle/gradle.properties` 中配置 `gtodysseyReleasesUsername` 和 `gtodysseyReleasesPassword`。
