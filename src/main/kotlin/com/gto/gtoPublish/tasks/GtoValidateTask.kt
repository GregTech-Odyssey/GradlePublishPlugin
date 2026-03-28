package com.gto.gtoPublish.tasks

import com.gto.gtoPublish.VersionChecker
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class GtoValidateTask : DefaultTask() {

    @get:Input
    abstract val enableMaven: Property<Boolean>

    @get:Input
    abstract val enableGithub: Property<Boolean>

    @get:Input
    abstract val enableCurseforge: Property<Boolean>

    @get:Input
    abstract val mavenRepoName: Property<String>

    @get:Input
    abstract val mavenRepoUrl: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val archivesName: Property<String>

    @get:Input @get:Optional
    abstract val mavenUsername: Property<String>

    @get:Input @get:Optional
    abstract val mavenPassword: Property<String>

    @get:Input @get:Optional
    abstract val githubToken: Property<String>

    @get:Input @get:Optional
    abstract val githubRepo: Property<String>

    @get:Input @get:Optional
    abstract val curseforgeToken: Property<String>

    @get:Input @get:Optional
    abstract val curseforgeProjectId: Property<String>

    @get:Input @get:Optional
    abstract val minecraftVersion: Property<String>

    @get:Input @get:Optional
    abstract val curseforgeModLoader: Property<String>

    @get:Input @get:Optional
    abstract val curseforgeJavaVersion: Property<String>

    init {
        group = "gto publishing"
        description = "Validate credentials and version availability before publishing"
    }

    @TaskAction
    fun validate() {
        val errors = mutableListOf<String>()
        val ver = projectVersion.get()
        val repoName = mavenRepoName.get()

        // --- Version format ---
        VersionChecker.checkVersionFormat(ver)

        // --- Maven credentials ---
        if (enableMaven.get()) {
            if (!mavenUsername.isPresent || mavenUsername.get().isBlank()) {
                errors += """Missing: ${repoName}Username
      ┌─ 设置方式: 在 ~/.gradle/gradle.properties 中添加:
      │    ${repoName}Username=你的用户名
      └─ 获取方式: 联系 xinxinsuried 获取 Maven 仓库访问权限"""
            }
            if (!mavenPassword.isPresent || mavenPassword.get().isBlank()) {
                errors += """Missing: ${repoName}Password
      ┌─ 设置方式: 在 ~/.gradle/gradle.properties 中添加:
      │    ${repoName}Password=你的密码
      └─ 获取方式: 联系 xinxinsuried 获取 Maven 仓库访问权限"""
            }
        }

        // --- GitHub credentials ---
        if (enableGithub.get()) {
            if (!githubToken.isPresent || githubToken.get().isBlank()) {
                errors += """Missing: gtoGithubToken
      ┌─ 设置方式: 在 ~/.gradle/gradle.properties 中添加:
      │    gtoGithubToken=ghp_xxxxxxxxxxxx
      └─ 获取方式: GitHub → Settings → Developer settings
         → Personal access tokens → Tokens (classic) → Generate new token
         勾选 repo 权限 (Full control of private repositories)"""
            }
            if (!githubRepo.isPresent || githubRepo.get().isBlank()) {
                errors += """Missing: githubRepo
      ┌─ 设置方式: 在 gtoPublish {} 扩展块中添加:
      │    githubRepo = 'owner/repo-name'
      └─ 示例: githubRepo = 'GTO-Odyssey/RegistryLib3'"""
            }
        }

        // --- CurseForge credentials ---
        if (enableCurseforge.get()) {
            if (!curseforgeToken.isPresent || curseforgeToken.get().isBlank()) {
                errors += """Missing: gtoCurseforgeToken
      ┌─ 设置方式: 在 ~/.gradle/gradle.properties 中添加:
      │    gtoCurseforgeToken=你的API密钥
      └─ 获取方式: https://www.curseforge.com/account/api-tokens"""
            }
            if (!curseforgeProjectId.isPresent || curseforgeProjectId.get().isBlank()) {
                errors += """Missing: curseforgeProjectId
      ┌─ 设置方式: 在 gtoPublish {} 扩展块中添加:
      │    curseforgeProjectId = '123456'
      └─ 获取方式: CurseForge 项目页面 → About This Project → Project ID"""
            }
            if (!curseforgeModLoader.isPresent || curseforgeModLoader.get().isBlank()) {
                errors += """Missing: curseforgeModLoader
      ┌─ 设置方式: 在 gtoPublish {} 扩展块中添加:
      │    curseforgeModLoader = 'NeoForge'
      └─ 可选值: NeoForge, Forge, Fabric, Quilt 等"""
            }
            if (!curseforgeJavaVersion.isPresent || curseforgeJavaVersion.get().isBlank()) {
                errors += """Missing: curseforgeJavaVersion
      ┌─ 设置方式: 在 gtoPublish {} 扩展块中添加:
      │    curseforgeJavaVersion = 'Java 25'
      └─ 可选值: Java 8, Java 17, Java 21, Java 25 等"""
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                "凭证校验失败 / Credential validation failed:\n\n  " + errors.joinToString("\n\n  ") +
                    "\n\n提示: ~/.gradle/gradle.properties 位于:\n" +
                    "  Windows: C:\\Users\\你的用户名\\.gradle\\gradle.properties\n" +
                    "  macOS/Linux: ~/.gradle/gradle.properties\n\n" +
                    "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
            )
        }
        logger.lifecycle("\u2713 所有凭证已配置")

        // --- Version conflict checks ---
        val versionErrors = mutableListOf<String>()

        if (enableMaven.get()) {
            try {
                VersionChecker.checkMavenVersionNotExists(
                    mavenRepoUrl.get(), projectGroup.get(), archivesName.get(), ver, logger
                )
            } catch (e: GradleException) {
                versionErrors += e.message ?: "Maven 版本冲突"
            }
        }

        if (enableGithub.get() && githubRepo.isPresent && githubToken.isPresent) {
            try {
                VersionChecker.checkGithubReleaseNotExists(
                    githubRepo.get(), githubToken.get(), ver, logger
                )
            } catch (e: GradleException) {
                versionErrors += e.message ?: "GitHub 版本冲突"
            }
        }

        if (versionErrors.isNotEmpty()) {
            throw GradleException(
                "版本冲突 / Version conflict:\n  - " + versionErrors.joinToString("\n  - ") +
                    "\n\n请先修改 gradle.properties 中的 mod_version 再发布。\n" +
                    "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
            )
        }
        logger.lifecycle("\u2713 版本 $ver 在所有目标平台可用")
    }
}
