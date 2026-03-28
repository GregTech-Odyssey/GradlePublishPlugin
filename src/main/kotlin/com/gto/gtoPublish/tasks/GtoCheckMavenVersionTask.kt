package com.gto.gtoPublish.tasks

import com.gto.gtoPublish.VersionChecker
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class GtoCheckMavenVersionTask : DefaultTask() {

    @get:Input
    abstract val mavenRepoUrl: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val archivesName: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    init {
        group = "gto publishing"
        description = "Check that the version does not already exist in Maven repository"
    }

    @TaskAction
    fun check() {
        val ver = projectVersion.get()
        VersionChecker.checkVersionFormat(ver)
        VersionChecker.checkMavenVersionNotExists(
            mavenRepoUrl.get(), projectGroup.get(), archivesName.get(), ver, logger
        )
        logger.lifecycle("\u2713 Maven 版本 $ver 可用")
    }
}
