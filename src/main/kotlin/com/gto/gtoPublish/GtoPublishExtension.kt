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

    /** Maven 仓库名称，需匹配 publishing.repositories.maven.name (默认 gtodysseyReleases) */
    abstract val mavenRepoName: Property<String>

    /** Maven 仓库 URL (默认 https://maven.gtodyssey.com/releases) */
    abstract val mavenRepoUrl: Property<String>

    /** CurseForge 游戏版本列表 (默认 ['26.1', 'NeoForge', 'Java 25']) */
    abstract val curseforgeGameVersions: ListProperty<String>

    init {
        publishMaven.convention(true)
        publishGithub.convention(false)
        publishCurseforge.convention(false)
        mavenRepoName.convention("gtodysseyReleases")
        mavenRepoUrl.convention("https://maven.gtodyssey.com/releases")
        curseforgeGameVersions.convention(listOf("26.1", "NeoForge", "Java 25"))
    }
}
