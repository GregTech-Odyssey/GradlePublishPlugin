package com.gto.gtoPublish

import org.gradle.api.provider.Property

abstract class GtoPublishExtension {

    /** 是否发布到 Maven 仓库 (默认 true) */
    abstract val publishMaven: Property<Boolean>

    /** 是否发布到 GitHub Release (默认 false) */
    abstract val publishGithub: Property<Boolean>

    /** 是否发布到 CurseForge (默认 false) */
    abstract val publishCurseforge: Property<Boolean>

    /** Maven 仓库名称，需匹配 publishing.repositories.maven.name (默认 gtodysseyRepository) */
    abstract val mavenRepoName: Property<String>

    /**
     * Maven 仓库 URL，可选值:
     *   "releases" → https://maven.gtodyssey.com/releases
     *   "private"  → https://maven.gtodyssey.com/private
     */
    abstract val mavenRepoUrl: Property<String>

    /** GitHub 仓库 (格式: owner/repo-name) */
    abstract val githubRepo: Property<String>

    /** CurseForge 项目 ID */
    abstract val curseforgeProjectId: Property<String>

    /** Minecraft 版本号（必填，如 26.1） */
    abstract val minecraftVersion: Property<String>

    /** 模组加载器（必填，如 NeoForge、Forge、Fabric），小写后用于 artifactId 和文件名 */
    abstract val modLoader: Property<String>

    /** CurseForge Java 版本标签，如 Java 25、Java 21 等 */
    abstract val curseforgeJavaVersion: Property<String>

    init {
        publishMaven.convention(true)
        publishGithub.convention(false)
        publishCurseforge.convention(false)
        mavenRepoName.convention("gtodysseyRepository")
        githubRepo.convention("")
        curseforgeProjectId.convention("")
        curseforgeJavaVersion.convention("")
    }

    companion object {
        private val REPO_URL_MAP = mapOf(
            "releases" to "https://maven.gtodyssey.com/releases",
            "private" to "https://maven.gtodyssey.com/private"
        )

        /** 将 "releases" / "private" 简写解析为完整 URL */
        fun resolveRepoUrl(input: String): String {
            return REPO_URL_MAP[input.lowercase()]
                ?: throw org.gradle.api.GradleException(
                    "mavenRepoUrl 值 '$input' 无效 / Invalid mavenRepoUrl value\n" +
                        "可选值 / Valid options: releases, private"
                )
        }
    }
}
