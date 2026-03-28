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

        // Find main JAR
        val mainJar = libsDir.listFiles()?.filter {
            it.name.endsWith(".jar") &&
                !it.name.contains("-dev") &&
                !it.name.contains("-sources") &&
                !it.name.contains("-javadoc")
        }?.firstOrNull() ?: throw GradleException("build/libs/ 下未找到 JAR 文件 / No JAR found in build/libs/\n详情请参阅 / See: ${VersionChecker.DOCS_URL}")

        // 强制校验 Maven 制品存在且与本地一致（一键流中 Maven 刚发布则跳过）
        if (skipMavenConsistencyCheck.getOrElse(false)) {
            logger.lifecycle("  ⏭ 跳过 Maven SHA-1 校验（Maven 已在本次构建中发布） / Skipping Maven SHA-1 check (published in same build)")
        } else {
            VersionChecker.requireMavenArtifactConsistent(
                mavenRepoUrl.get(), projectGroup.get(), archivesName.get(),
                minecraftVersion.get(), ver, mainJar, logger
            )
        }

        // 通过 CurseForge API 自动获取 MC 版本对应的 gameVersionId
        val mcGameVersionId = VersionChecker.fetchCurseForgeMinecraftVersionId(mcVersion, logger)

        // 解析模组加载器和 Java 版本标签
        val allTags = listOf(cfModLoader, cfJavaVersion)
        logger.lifecycle("  正在解析 CurseForge 版本标签: 模组加载器=$cfModLoader, Java版本=$cfJavaVersion ...")
        val versionsConn = URI("https://minecraft.curseforge.com/api/game/versions")
            .toURL().openConnection() as HttpURLConnection
        versionsConn.setRequestProperty("X-Api-Token", cfToken)
        versionsConn.setRequestProperty("User-Agent", "GtoPublishPlugin")
        versionsConn.connectTimeout = 10000
        versionsConn.readTimeout = 10000

        val versionsResponseCode = versionsConn.responseCode
        if (versionsResponseCode != 200) {
            val errorBody = versionsConn.errorStream?.bufferedReader()?.readText() ?: "unknown"
            versionsConn.disconnect()
            if (versionsResponseCode == 403) {
                throw GradleException(
                    "CurseForge API 返回 403 Forbidden，请检查 Token 是否正确且具有上传权限\n" +
                    "CurseForge API returned 403 Forbidden. Verify your token is valid and has upload permissions.\n" +
                    "Token 获取地址 / Get token at: https://www.curseforge.com/account/api-tokens\n" +
                    "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
                )
            }
            throw GradleException(
                "CurseForge 游戏版本 API 请求失败 / CurseForge game versions API failed ($versionsResponseCode): $errorBody\n" +
                "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
            )
        }
        val allVersionsText = versionsConn.inputStream.bufferedReader().readText()
        versionsConn.disconnect()

        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val allVersions: List<Map<String, Any>> = Gson().fromJson(allVersionsText, listType)

        // MC 版本 ID (从上传 API 的版本列表中查找)
        val versionIds = mutableListOf<Int>()
        val mcMatched = allVersions.find { it["name"] == mcVersion }
        if (mcMatched != null) {
            versionIds += (mcMatched["id"] as Double).toInt()
            logger.lifecycle("  ✓ MC 版本 $mcVersion → ID ${versionIds.last()}")
        } else {
            // 使用 CurseForge v1 API 返回的 gameVersionId 作为后备
            versionIds += mcGameVersionId
            logger.lifecycle("  ✓ MC 版本 $mcVersion → gameVersionId $mcGameVersionId (via v1 API)")
        }

        // 模组加载器和 Java 版本标签
        for (target in allTags) {
            val matched = allVersions.find { it["name"] == target }
            if (matched != null) {
                versionIds += (matched["id"] as Double).toInt()
                logger.lifecycle("  ✓ $target → ID ${versionIds.last()}")
            } else {
                throw GradleException(
                    "无法解析 CurseForge 游戏版本标签: '$target'\n" +
                    "Failed to resolve CurseForge game version tag: '$target'\n" +
                    "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
                )
            }
        }
        if (versionIds.isEmpty()) {
            throw GradleException("无法解析任何 CurseForge 游戏版本 ID / Failed to resolve any CurseForge game version ID\n详情请参阅 / See: ${VersionChecker.DOCS_URL}")
        }

        // Upload via multipart
        val boundary = "----GtoPublish${System.nanoTime()}"
        val releaseType = VersionChecker.parseReleaseType(ver)
        val metadata = Gson().toJson(
            mapOf(
                "changelog" to "Release $ver",
                "changelogType" to "markdown",
                "displayName" to "${archivesName.get()}-${ver}.jar",
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
