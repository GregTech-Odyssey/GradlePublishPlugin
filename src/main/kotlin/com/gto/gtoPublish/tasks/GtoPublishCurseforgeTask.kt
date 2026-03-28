package com.gto.gtoPublish.tasks

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gto.gtoPublish.VersionChecker
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URI

abstract class GtoPublishCurseforgeTask : DefaultTask() {

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val curseforgeToken: Property<String>

    @get:Input
    abstract val curseforgeProjectId: Property<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val modLoader: Property<String>

    @get:Input
    abstract val javaVersion: Property<String>

    @get:Input
    abstract val archivesName: Property<String>

    @get:Input
    abstract val mavenRepoUrl: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val skipMavenConsistencyCheck: Property<Boolean>

    @get:InputDirectory
    lateinit var libsDir: File

    init {
        group = "gto publishing"
        description = "Upload JAR to CurseForge"
    }

    @TaskAction
    fun publish() {
        val ver = projectVersion.get()
        val cfToken = curseforgeToken.get()
        val cfProjectId = curseforgeProjectId.get()
        val cfModLoader = modLoader.get()
        val cfJavaVersion = javaVersion.get()

        // MC 版本从 minecraft_version 属性读取
        val mcVersion = minecraftVersion.get()

        // Find main JAR — must match archivesName + version
        val displayVer = VersionChecker.displayVersion(ver)
        val expectedName = "${archivesName.get()}-${displayVer}.jar"
        val mainJar = libsDir.listFiles()?.find {
            it.name == expectedName
        } ?: throw GradleException("build/libs/ 下未找到 '$expectedName' / Expected JAR '$expectedName' not found in build/libs/\n详情请参阅 / See: ${VersionChecker.DOCS_URL}")

        // 强制校验 Maven 制品存在且与本地一致（一键流中 Maven 刚发布则跳过）
        if (skipMavenConsistencyCheck.getOrElse(false)) {
            logger.lifecycle("  ⏭ 跳过 Maven SHA-1 校验（Maven 已在本次构建中发布） / Skipping Maven SHA-1 check (published in same build)")
        } else {
            VersionChecker.requireMavenArtifactConsistent(
                mavenRepoUrl.get(), projectGroup.get(), archivesName.get(),
                ver, mainJar, logger
            )
        }

        // 从 CurseForge Upload API 获取所有游戏版本（MC、modLoader、Java）
        logger.lifecycle("  正在从 CurseForge API 获取游戏版本列表 ...")
        val versionsConn = URI("https://minecraft.curseforge.com/api/game/versions")
            .toURL().openConnection() as HttpURLConnection
        versionsConn.setRequestProperty("X-Api-Token", cfToken)
        versionsConn.setRequestProperty("User-Agent", "GtoPublishPlugin")
        versionsConn.setRequestProperty("Accept", "application/json")
        versionsConn.connectTimeout = 10000
        versionsConn.readTimeout = 10000

        val versionsResponseCode = versionsConn.responseCode
        if (versionsResponseCode != 200) {
            val errorBody = versionsConn.errorStream?.bufferedReader()?.readText() ?: "unknown"
            versionsConn.disconnect()
            throw GradleException(
                "CurseForge 游戏版本 API 请求失败 ($versionsResponseCode): $errorBody\n" +
                "CurseForge game versions API failed.\n" +
                "请确认 Token 有效且具有上传权限。\n" +
                "Token 获取地址 / Get token at: https://authors.curseforge.com/account/api-tokens\n" +
                "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
            )
        }
        val allVersionsText = versionsConn.inputStream.bufferedReader().readText()
        versionsConn.disconnect()

        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val allVersions: List<Map<String, Any>> = Gson().fromJson(allVersionsText, listType)

        // 从同一个响应中查找 MC 版本、modLoader、Java 版本的 ID
        val allTargets = listOf(mcVersion, cfModLoader, cfJavaVersion)
        val versionIds = mutableListOf<Int>()

        for (target in allTargets) {
            val matched = allVersions.find { it["name"] == target }
            if (matched != null) {
                versionIds += (matched["id"] as Double).toInt()
                logger.lifecycle("  ✓ $target → ID ${versionIds.last()}")
            } else {
                throw GradleException(
                    "无法在 CurseForge 游戏版本列表中找到: '$target'\n" +
                    "Failed to resolve CurseForge game version: '$target'\n" +
                    "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
                )
            }
        }

        // Upload via multipart
        val boundary = "----GtoPublish${System.nanoTime()}"
        val releaseType = VersionChecker.parseReleaseType(ver)
        val metadata = Gson().toJson(
            mapOf(
                "changelog" to "Release $ver",
                "changelogType" to "markdown",
                "displayName" to mainJar.name,
                "gameVersions" to versionIds,
                "releaseType" to releaseType
            )
        )

        val uploadUrl = "https://minecraft.curseforge.com/api/projects/${cfProjectId}/upload-file"
        val conn = URI(uploadUrl).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-Api-Token", cfToken)
        conn.setRequestProperty("User-Agent", "GtoPublishPlugin")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.doOutput = true
        conn.connectTimeout = 60000
        conn.readTimeout = 60000

        conn.outputStream.use { os ->
            val writer = PrintWriter(OutputStreamWriter(os, Charsets.UTF_8), true)
            writer.append("--${boundary}\r\n")
            writer.append("Content-Disposition: form-data; name=\"metadata\"\r\n")
            writer.append("Content-Type: application/json\r\n\r\n")
            writer.append(metadata).append("\r\n")
            writer.flush()
            writer.append("--${boundary}\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${mainJar.name}\"\r\n")
            writer.append("Content-Type: application/java-archive\r\n\r\n")
            writer.flush()
            mainJar.inputStream().use { it.copyTo(os) }
            os.flush()
            writer.append("\r\n--${boundary}--\r\n")
            writer.flush()
        }

        if (conn.responseCode !in listOf(200, 201)) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown error"
            throw GradleException("CurseForge 上传失败 / CurseForge upload failed (${conn.responseCode}): $error\n详情请参阅 / See: ${VersionChecker.DOCS_URL}")
        }
        logger.lifecycle("\u2713 已上传至 CurseForge: ${mainJar.name}")
        conn.disconnect()
    }
}
