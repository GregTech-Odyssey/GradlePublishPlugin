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

    /** Maven 仓库 URL (默认 https://maven.gtodyssey.com/releases) */
    abstract val mavenRepoUrl: Property<String>

    /** GitHub 仓库 (格式: owner/repo-name) */
    abstract val githubRepo: Property<String>

    /** CurseForge 项目 ID */
    abstract val curseforgeProjectId: Property<String>

    /** Minecraft 版本号（默认 26.1） */
    abstract val minecraftVersion: Property<String>

    /** CurseForge 模组加载器标签，如 NeoForge、Forge、Fabric 等 */
    abstract val curseforgeModLoader: Property<String>

    /** CurseForge Java 版本标签，如 Java 25、Java 21 等 */
    abstract val curseforgeJavaVersion: Property<String>

    init {
        publishMaven.convention(true)
        publishGithub.convention(false)
        publishCurseforge.convention(false)
        mavenRepoName.convention("gtodysseyRepository")
        mavenRepoUrl.convention("https://maven.gtodyssey.com/releases")
        githubRepo.convention("")
        curseforgeProjectId.convention("")
        minecraftVersion.convention("26.1")
        curseforgeModLoader.convention("")
        curseforgeJavaVersion.convention("")
    }
}
