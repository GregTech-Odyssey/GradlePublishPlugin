package com.gto.gtoPublish

import org.gradle.api.GradleException
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

    /**
     * CurseForge Environment 组（必填，如 both、client、server）
     */
    abstract val curseforgeEnvironment: Property<String>

    init {
        publishMaven.convention(true)
        publishGithub.convention(false)
        publishCurseforge.convention(false)
        mavenRepoName.convention("gtodysseyRepository")
        githubRepo.convention("")
        curseforgeProjectId.convention("")
        curseforgeJavaVersion.convention("")
        curseforgeEnvironment.convention("both")
    }

    companion object {
        private val REPO_URL_MAP = mapOf(
            "releases" to "https://maven.gtodyssey.com/releases",
            "private" to "https://maven.gtodyssey.com/private"
        )

        /** CurseForge API 中 Environment 组的标准名称 */
        const val CF_ENV_CLIENT = "Client"
        const val CF_ENV_SERVER = "Server"

        /** 将 "releases" / "private" 简写解析为完整 URL */
        fun resolveRepoUrl(input: String): String {
            return REPO_URL_MAP[input.lowercase()]
                ?: throw GradleException(
                    "mavenRepoUrl 值 '$input' 无效 / Invalid mavenRepoUrl value\n" +
                        "可选值 / Valid options: releases, private"
                )
        }

        /**
         * 将配置值解析为 CurseForge gameVersions 中的 Environment 名称列表。
         * 返回值与 CurseForge `/api/game/versions` 的 `name` 字段一致（Client / Server）。
         */
        fun resolveEnvironmentVersions(input: String): List<String> {
            return when (input.trim().lowercase()) {
                "both", "client-server", "client_and_server", "client+server", "all" ->
                    listOf(CF_ENV_CLIENT, CF_ENV_SERVER)
                "client", "client-only", "client_only" ->
                    listOf(CF_ENV_CLIENT)
                "server", "server-only", "server_only" ->
                    listOf(CF_ENV_SERVER)
                else -> throw GradleException(
                    "curseforgeEnvironment 值 '$input' 无效 / Invalid curseforgeEnvironment value\n" +
                        "可选值 / Valid options: both, client, server\n" +
                        "  both   → Client + Server（双端）\n" +
                        "  client → 仅 Client\n" +
                        "  server → 仅 Server"
                )
            }
        }
    }
}
