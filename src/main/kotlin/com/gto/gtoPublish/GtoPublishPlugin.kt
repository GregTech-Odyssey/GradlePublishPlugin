package com.gto.gtoPublish

import com.gto.gtoPublish.tasks.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension

class GtoPublishPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // 检查插件是否有新版本
        val pluginVersion = VersionChecker.getPluginVersion()
        if (pluginVersion != null) {
            VersionChecker.checkPluginUpdate(
                "https://maven.gtodyssey.com/releases",
                "com.gto",
                "gtopublishgradleplugin",
                pluginVersion,
                project.logger
            )
        }

        val ext = project.extensions.create("gtoPublish", GtoPublishExtension::class.java)

        // 通过 gradle.properties 覆盖扩展默认值
        applyPropertyOverrides(project, ext)

        project.afterEvaluate {
            val originalVersion = project.version.toString()
            VersionChecker.checkVersionFormat(originalVersion)
            val displayVer = VersionChecker.displayVersion(originalVersion)

            // 将 project.version 设为显示版本（去除 -release）
            // 这样 assemble 产生的 JAR、Maven 发布的制品都不包含 -release
            project.version = displayVer
            project.logger.lifecycle("版本: $originalVersion → 显示版本: $displayVer")

            val enableMaven = ext.publishMaven.get()
            val enableGithub = ext.publishGithub.get()
            val enableCurseforge = ext.publishCurseforge.get()
            val repoName = ext.mavenRepoName.get()

            // --- gtoValidate: 凭证 + 全局版本校验 ---
            project.tasks.register("gtoValidate", GtoValidateTask::class.java) { task ->
                task.enableMaven.set(ext.publishMaven)
                task.enableGithub.set(ext.publishGithub)
                task.enableCurseforge.set(ext.publishCurseforge)
                task.mavenRepoName.set(ext.mavenRepoName)
                task.mavenRepoUrl.set(ext.mavenRepoUrl)
                task.projectVersion.set(project.provider { project.version.toString() })
                task.projectGroup.set(project.provider { project.group.toString() })
                task.archivesName.set(project.provider {
                    project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                })
                task.mavenUsername.set(project.provider {
                    project.findProperty("${repoName}Username")?.toString()
                })
                task.mavenPassword.set(project.provider {
                    project.findProperty("${repoName}Password")?.toString()
                })
                task.githubToken.set(project.provider { resolveGithubToken(project) })
                task.githubRepo.set(ext.githubRepo)
                task.curseforgeToken.set(project.provider {
                    project.findProperty("gtoCurseforgeToken")?.toString()
                })
                task.curseforgeProjectId.set(ext.curseforgeProjectId)
            }

            // --- Maven: 版本检查 + 发布 ---
            if (enableMaven) {
                project.tasks.register("gtoCheckMavenVersion", GtoCheckMavenVersionTask::class.java) { task ->
                    task.mavenRepoUrl.set(ext.mavenRepoUrl)
                    task.projectGroup.set(project.provider { project.group.toString() })
                    task.archivesName.set(project.provider {
                        project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                    })
                    task.projectVersion.set(project.provider { project.version.toString() })
                }

                // 查找匹配仓库名的 publish 任务（兼容 maven-publish 延迟注册）
                val repoNameCapitalized = repoName.replaceFirstChar { c -> c.uppercase() }
                val mavenPublishTask = project.tasks.names
                    .filter { it.startsWith("publish") && it.contains(repoNameCapitalized) }
                    .firstOrNull()
                    ?: project.tasks.names.find { it == "publish" }

                if (mavenPublishTask == null) {
                    project.logger.error(
                        "╔══════════════════════════════════════════════════════════════╗\n" +
                        "║  GTO Publish Plugin — Maven 发布配置错误                     ║\n" +
                        "╠══════════════════════════════════════════════════════════════╣\n" +
                        "║  找不到名称包含 '$repoName' 的 publish 任务。                ║\n" +
                        "║  No publish task found containing '$repoName'.               ║\n" +
                        "║                                                              ║\n" +
                        "║  请确保项目已应用 maven-publish 插件并配置了仓库:             ║\n" +
                        "║  Make sure maven-publish plugin is applied with repository:  ║\n" +
                        "║                                                              ║\n" +
                        "║    plugins { id 'maven-publish' }                            ║\n" +
                        "║    publishing {                                               ║\n" +
                        "║      repositories {                                           ║\n" +
                        "║        maven {                                                ║\n" +
                        "║          name = '$repoName'                                  ║\n" +
                        "║          url = '...'                                          ║\n" +
                        "║        }                                                      ║\n" +
                        "║      }                                                        ║\n" +
                        "║    }                                                          ║\n" +
                        "║                                                              ║\n" +
                        "║  文档 / Docs: ${VersionChecker.DOCS_URL}\n" +
                        "╚══════════════════════════════════════════════════════════════╝"
                    )
                    throw org.gradle.api.GradleException(
                        "Maven publish task not found. Apply 'maven-publish' plugin and configure a repository named '$repoName'.\n" +
                        "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
                    )
                }

                project.tasks.register("gtoPublishMaven") { task ->
                    task.group = "gto publishing"
                    task.description = "Publish to Maven repository ($repoName)"
                    task.dependsOn("gtoCheckMavenVersion")
                    task.dependsOn(mavenPublishTask)
                    task.mustRunAfter("gtoValidate", "assemble")
                }

                // 确保版本检查在实际发布之前
                project.tasks.named(mavenPublishTask).configure {
                    it.mustRunAfter("gtoCheckMavenVersion")
                }
            }

            // --- GitHub: 发布任务内置版本检查 ---
            if (enableGithub) {
                project.tasks.register("gtoPublishGithub", GtoPublishGithubTask::class.java) { task ->
                    task.projectVersion.set(project.provider { project.version.toString() })
                    task.githubToken.set(project.provider { resolveGithubToken(project) })
                    task.githubRepo.set(ext.githubRepo)
                    task.mavenRepoUrl.set(ext.mavenRepoUrl)
                    task.projectGroup.set(project.provider { project.group.toString() })
                    task.archivesName.set(project.provider {
                        project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                    })
                    task.libsDir = project.layout.buildDirectory.dir("libs").get().asFile
                    task.mustRunAfter("gtoValidate", "assemble")
                }
            }

            // --- CurseForge: 发布任务（Maven 一致性校验 + 上传） ---
            if (enableCurseforge) {
                project.tasks.register("gtoPublishCurseforge", GtoPublishCurseforgeTask::class.java) { task ->
                    task.projectVersion.set(project.provider { project.version.toString() })
                    task.curseforgeToken.set(project.provider {
                        project.findProperty("gtoCurseforgeToken")?.toString()
                    })
                    task.curseforgeProjectId.set(ext.curseforgeProjectId)
                    task.gameVersions.set(ext.curseforgeGameVersions)
                    task.archivesName.set(project.provider {
                        project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                    })
                    task.mavenRepoUrl.set(ext.mavenRepoUrl)
                    task.projectGroup.set(project.provider { project.group.toString() })
                    task.libsDir = project.layout.buildDirectory.dir("libs").get().asFile
                    task.mustRunAfter("gtoValidate", "assemble")
                }
            }

            // --- gtoPublish: 总入口 ---
            project.tasks.register("gtoPublish") { task ->
                task.group = "gto publishing"
                task.description = "Full publish: validate → build → all enabled targets"
                task.dependsOn("gtoValidate", "assemble")
                if (enableMaven) task.dependsOn("gtoPublishMaven")
                if (enableGithub) task.dependsOn("gtoPublishGithub")
                if (enableCurseforge) task.dependsOn("gtoPublishCurseforge")

                task.doFirst {
                    val targets = mutableListOf<String>()
                    if (enableMaven) targets += "Maven"
                    if (enableGithub) targets += "GitHub Release"
                    if (enableCurseforge) targets += "CurseForge"
                    project.logger.lifecycle("发布目标: ${targets.joinToString(", ")}")
                }
            }

            // Enforce execution order
            project.tasks.named("assemble").configure { it.mustRunAfter("gtoValidate") }
        }
    }

    private fun applyPropertyOverrides(project: Project, ext: GtoPublishExtension) {
        project.findProperty("gtoPublishMaven")?.let {
            ext.publishMaven.set(it.toString().toBoolean())
        }
        project.findProperty("gtoPublishGithub")?.let {
            ext.publishGithub.set(it.toString().toBoolean())
        }
        project.findProperty("gtoPublishCurseforge")?.let {
            ext.publishCurseforge.set(it.toString().toBoolean())
        }
        project.findProperty("gtoMavenRepoName")?.let {
            ext.mavenRepoName.set(it.toString())
        }
        project.findProperty("gtoMavenRepoUrl")?.let {
            ext.mavenRepoUrl.set(it.toString())
        }
        project.findProperty("gtoGithubRepo")?.let {
            ext.githubRepo.set(it.toString())
        }
        project.findProperty("gtoCurseforgeProjectId")?.let {
            ext.curseforgeProjectId.set(it.toString())
        }
        project.findProperty("gtoCurseforgeGameVersions")?.let {
            ext.curseforgeGameVersions.set(it.toString().split(",").map { s -> s.trim() })
        }
    }

    private fun resolveGithubToken(project: Project): String? {
        return project.findProperty("gtoGithubToken")?.toString()
            ?: System.getenv("GH_TOKEN")
            ?: System.getenv("GITHUB_TOKEN")
    }
}
