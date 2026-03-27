package com.gto.gtoPublish.tasks

import com.google.gson.Gson
import com.gto.gtoPublish.VersionChecker
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

abstract class GtoPublishGithubTask : DefaultTask() {

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val githubToken: Property<String>

    @get:Input
    abstract val githubRepo: Property<String>

    @get:Input
    abstract val mavenRepoUrl: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val archivesName: Property<String>

    @get:InputDirectory
    lateinit var libsDir: File

    init {
        group = "gto publishing"
        description = "Create GitHub Release and upload JAR artifacts"
    }

    @TaskAction
    fun publish() {
        val ver = projectVersion.get()
        val ghToken = githubToken.get()
        val ghRepo = githubRepo.get()

        // 发布前强制检查版本是否已存在
        VersionChecker.checkGithubReleaseNotExists(ghRepo, ghToken, ver, logger)

        // 强制校验 Maven 制品存在且与本地一致
        val jarsForCheck = libsDir.listFiles()?.filter {
            it.name.endsWith(".jar") &&
                !it.name.contains("-dev") &&
                !it.name.contains("-sources") &&
                !it.name.contains("-javadoc")
        } ?: emptyList()
        if (jarsForCheck.isNotEmpty()) {
            VersionChecker.requireMavenArtifactConsistent(
                mavenRepoUrl.get(), projectGroup.get(), archivesName.get(),
                ver, jarsForCheck.first(), logger
            )
        }

        // Create release
        val conn = URI("https://api.github.com/repos/${ghRepo}/releases")
            .toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "token $ghToken")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val releaseType = VersionChecker.parseReleaseType(ver)
        val isPreRelease = releaseType != "release"
        val label = when (releaseType) {
            "alpha" -> "[Alpha]"
            "beta" -> "[Beta]"
            else -> ""
        }
        val body = Gson().toJson(
            mapOf(
                "tag_name" to ver,
                "name" to "${label} ${ver}".trim(),
                "body" to "Release $ver ($releaseType)",
                "draft" to false,
                "prerelease" to isPreRelease
            )
        )
        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

        if (conn.responseCode !in listOf(200, 201)) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown error"
            throw GradleException("GitHub Release 创建失败 / Failed to create GitHub Release (${conn.responseCode}): $error\n详情请参阅 / See: ${VersionChecker.DOCS_URL}")
        }

        val responseText = conn.inputStream.bufferedReader().readText()
        val uploadUrlRegex = """"upload_url"\s*:\s*"([^"]+)"""".toRegex()
        val match = uploadUrlRegex.find(responseText)
            ?: throw GradleException("无法从 GitHub 响应中解析 upload_url / Failed to parse upload_url from GitHub response\n详情请参阅 / See: ${VersionChecker.DOCS_URL}")
        val uploadUrl = match.groupValues[1].replace("{?name,label}", "")
        conn.disconnect()

        // Upload JARs (exclude dev/sources/javadoc)
        val jars = libsDir.listFiles()?.filter {
            it.name.endsWith(".jar") &&
                !it.name.contains("-dev") &&
                !it.name.contains("-sources") &&
                !it.name.contains("-javadoc")
        } ?: emptyList()

        for (jar in jars) {
            logger.lifecycle("  Uploading ${jar.name} ...")
            val encodedName = URLEncoder.encode(jar.name, "UTF-8")
            val uc = URI("${uploadUrl}?name=${encodedName}")
                .toURL().openConnection() as HttpURLConnection
            uc.requestMethod = "POST"
            uc.setRequestProperty("Authorization", "token $ghToken")
            uc.setRequestProperty("Content-Type", "application/java-archive")
            uc.setRequestProperty("Content-Length", jar.length().toString())
            uc.doOutput = true
            jar.inputStream().use { input -> uc.outputStream.use { output -> input.copyTo(output) } }
            if (uc.responseCode !in listOf(200, 201)) {
                logger.error("  上传失败 ${jar.name}: ${uc.responseCode}")
            } else {
                logger.lifecycle("  \u2713 ${jar.name}")
            }
            uc.disconnect()
        }
        logger.lifecycle("\u2713 GitHub Release $ver 已创建")
    }
}
