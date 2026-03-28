package com.gto.gtoPublish

import com.gto.gtoPublish.tasks.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

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

            // 解析 mavenRepoUrl 简写为完整 URL
            if (ext.mavenRepoUrl.isPresent) {
                ext.mavenRepoUrl.set(GtoPublishExtension.resolveRepoUrl(ext.mavenRepoUrl.get()))
            }

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
                task.curseforgeModLoader.set(ext.curseforgeModLoader)
                task.curseforgeJavaVersion.set(ext.curseforgeJavaVersion)
                task.minecraftVersion.set(ext.minecraftVersion)
            }

            // --- Maven: 版本检查 + 暂存到本地 + 自定义路径上传 ---
            if (enableMaven) {
                // 配置 maven-publish：暂存到本地目录
                val stagingDir = project.layout.buildDirectory.dir("gto-maven-staging").get().asFile
                project.pluginManager.apply("maven-publish")
                project.extensions.configure(PublishingExtension::class.java) { publishing ->
                    // 注册暂存仓库
                    publishing.repositories.maven { repo ->
                        repo.name = "gtoStaging"
                        repo.url = stagingDir.toURI()
                    }
                    // 如果没有 publication，自动创建一个
                    if (publishing.publications.isEmpty()) {
                        publishing.publications.create("mavenJava", MavenPublication::class.java) { pub ->
                            pub.from(project.components.getByName("java"))
                        }
                    }
                }

                project.tasks.register("gtoCheckMavenVersion", GtoCheckMavenVersionTask::class.java) { task ->
                    task.mavenRepoUrl.set(ext.mavenRepoUrl)
                    task.projectGroup.set(project.provider { project.group.toString() })
                    task.archivesName.set(project.provider {
                        project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                    })
                    task.minecraftVersion.set(ext.minecraftVersion)
                    task.projectVersion.set(project.provider { project.version.toString() })
                }

                // 查找暂存发布任务
                val stagingPublishTask = project.tasks.names
                    .filter { it.startsWith("publish") && it.contains("GtoStaging") }
                    .firstOrNull()
                    ?: "publishAllPublicationsToGtoStagingRepository"

                project.tasks.register("gtoPublishMaven", GtoPublishMavenTask::class.java) { task ->
                    task.mavenRepoUrl.set(ext.mavenRepoUrl)
                    task.projectGroup.set(project.provider { project.group.toString() })
                    task.archivesName.set(project.provider {
                        project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                    })
                    task.minecraftVersion.set(ext.minecraftVersion)
                    task.projectVersion.set(project.provider { project.version.toString() })
                    task.mavenUsername.set(project.provider {
                        project.findProperty("${repoName}Username")?.toString() ?: ""
                    })
                    task.mavenPassword.set(project.provider {
                        project.findProperty("${repoName}Password")?.toString() ?: ""
                    })
                    task.stagingDir = stagingDir
                    task.dependsOn("gtoCheckMavenVersion")
                    task.dependsOn(stagingPublishTask)
                    task.mustRunAfter("gtoValidate", "assemble")
                }

                // 确保暂存发布在版本检查之后
                project.tasks.matching { it.name == stagingPublishTask }.configureEach {
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
                    task.minecraftVersion.set(ext.minecraftVersion)
                    task.libsDir = project.layout.buildDirectory.dir("libs").get().asFile
                    task.skipMavenConsistencyCheck.set(enableMaven)
                    task.mustRunAfter("gtoValidate", "assemble")
                    if (enableMaven) task.mustRunAfter("gtoPublishMaven")
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
                    task.minecraftVersion.set(ext.minecraftVersion)
                    task.modLoader.set(ext.curseforgeModLoader)
                    task.javaVersion.set(ext.curseforgeJavaVersion)
                    task.archivesName.set(project.provider {
                        project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                    })
                    task.mavenRepoUrl.set(ext.mavenRepoUrl)
                    task.projectGroup.set(project.provider { project.group.toString() })
                    task.libsDir = project.layout.buildDirectory.dir("libs").get().asFile
                    task.skipMavenConsistencyCheck.set(enableMaven)
                    task.mustRunAfter("gtoValidate", "assemble")
                    if (enableMaven) task.mustRunAfter("gtoPublishMaven")
                    if (enableGithub) task.mustRunAfter("gtoPublishGithub")
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
            ext.mavenRepoUrl.set(GtoPublishExtension.resolveRepoUrl(it.toString()))
        }
        project.findProperty("gtoGithubRepo")?.let {
            ext.githubRepo.set(it.toString())
        }
        project.findProperty("gtoCurseforgeProjectId")?.let {
            ext.curseforgeProjectId.set(it.toString())
        }
        project.findProperty("gtoCurseforgeMinecraftVersion")?.let {
            ext.minecraftVersion.set(it.toString())
        }
        project.findProperty("gtoCurseforgeModLoader")?.let {
            ext.curseforgeModLoader.set(it.toString())
        }
        project.findProperty("gtoCurseforgeJavaVersion")?.let {
            ext.curseforgeJavaVersion.set(it.toString())
        }
    }

    private fun resolveGithubToken(project: Project): String? {
        return project.findProperty("gtoGithubToken")?.toString()
            ?: System.getenv("GH_TOKEN")
            ?: System.getenv("GITHUB_TOKEN")
    }
}
