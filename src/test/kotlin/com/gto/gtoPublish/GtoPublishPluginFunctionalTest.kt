package com.gto.gtoPublish

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 端到端功能测试：验证插件在真实 Gradle 项目中的行为。
 * 网络不可用时，插件内部的版本检查会自动静默跳过。
 */
class GtoPublishPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeProject(
        version: String = "1.2.3",
        archivesName: String = "mylib",
        publishMaven: Boolean = false,
        publishCurseforge: Boolean = false,
        minecraftVersion: String = "",
        modLoader: String = ""
    ) {
        File(projectDir, "settings.gradle").writeText("rootProject.name = 'test-lib'")
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id 'java'
                id 'maven-publish'
                id 'com.gto.gtopublishgradleplugin'
            }
            version = '$version'
            group = 'com.example'
            base.archivesName = '$archivesName'

            gtoPublish {
                publishMaven      = $publishMaven
                publishCurseforge = $publishCurseforge
                mavenRepoUrl      = 'releases'
                minecraftVersion  = '$minecraftVersion'
                modLoader         = '$modLoader'
            }
            """.trimIndent()
        )
    }

    @Test
    fun `non-mc library keeps archivesName and skips curseforge`() {
        writeProject(publishCurseforge = true)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("gtoPublish", "--console=plain")
            .build()

        assertTrue(result.output.contains("非 MC 库模式"), "应打印非 MC 库模式日志")
        assertTrue(result.output.contains("跳过 CurseForge 发布"), "应提示跳过 CurseForge")

        val jar = File(projectDir, "build/libs/mylib-1.2.3.jar")
        assertTrue(jar.exists(), "应生成 mylib-1.2.3.jar (archivesName 不追加 modLoader/MC 版本)")
        assertTrue(
            !File(projectDir, "build/libs").listFiles().orEmpty().any { it.name.contains("neoforge") },
            "非 MC 模式下文件名不应包含加载器/MC 版本"
        )
    }

    @Test
    fun `non-mc library does not require curseforge credentials`() {
        // publishCurseforge=true 但未配置任何 CF 凭证，非 MC 模式下应正常通过 gtoValidate
        writeProject(publishCurseforge = true)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("gtoValidate", "--console=plain")
            .build()

        assertTrue(result.output.contains("所有凭证已配置"), "非 MC 模式不应要求 CurseForge 凭证")
    }

    @Test
    fun `mc mod requires both minecraftVersion and modLoader`() {
        writeProject(minecraftVersion = "26.1") // 只填了一个

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("gtoValidate", "--console=plain")
            .buildAndFail()

        assertTrue(result.output.contains("minecraftVersion 与 modLoader 必须同时填写"))
    }
}
