package com.gto.gtoPublish

import org.gradle.api.provider.ListProperty
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

    /** CurseForge 游戏版本列表 (默认 ['26.1', 'NeoForge', 'Java 25']) */
    abstract val curseforgeGameVersions: ListProperty<String>

    init {
        publishMaven.convention(true)
        publishGithub.convention(false)
        publishCurseforge.convention(false)
        mavenRepoName.convention("gtodysseyRepository")
        mavenRepoUrl.convention("https://maven.gtodyssey.com/releases")
        githubRepo.convention("")
        curseforgeProjectId.convention("")
        curseforgeGameVersions.convention(listOf("26.1", "NeoForge", "Java 25"))
    }
}
