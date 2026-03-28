package com.gto.gtoPublish.tasks

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
import java.util.Base64

/**
 * 从 maven-publish 的本地暂存目录中读取所有制品，
 * 以自定义路径布局上传到远程 Maven 仓库。
 *
 * 本地暂存路径 (maven-publish 标准):
 *   {stagingDir}/{group}/{artifactId}/{version}/...
 *
 * 远程目标路径 (自定义):
 *   {repoUrl}/{group}/{artifactId}/{minecraftVersion}/{version}/...
 */
abstract class GtoPublishMavenTask : DefaultTask() {

    @get:Input
    abstract val mavenRepoUrl: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val archivesName: Property<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val mavenUsername: Property<String>

    @get:Input
    abstract val mavenPassword: Property<String>

    @get:InputDirectory
    lateinit var stagingDir: File

    init {
        group = "gto publishing"
        description = "Upload staged Maven artifacts with custom path layout (group/artifactId/mcVersion/version)"
    }

    @TaskAction
    fun publish() {
        val ver = projectVersion.get()
        val grp = projectGroup.get()
        val artifactId = archivesName.get()
        val mcVer = minecraftVersion.get()
        val repoUrl = mavenRepoUrl.get().trimEnd('/')
        val username = mavenUsername.get()
        val password = mavenPassword.get()

        val groupPath = grp.replace('.', '/')

        // maven-publish 的本地暂存路径
        val localVersionDir = File(stagingDir, "${groupPath}/${artifactId}/${ver}")
        if (!localVersionDir.isDirectory) {
            throw GradleException(
                "本地暂存目录不存在: ${localVersionDir.absolutePath}\n" +
                    "请确保 maven-publish 已正确配置并在本任务前执行了暂存发布。\n" +
                    "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
            )
        }

        // 远程目标基础路径（插入 minecraftVersion）
        val remoteBasePath = "${repoUrl}/${groupPath}/${artifactId}/${mcVer}/${ver}"
        logger.lifecycle("Maven 上传路径: $remoteBasePath")

        // 上传 version 目录下的所有文件
        val files = localVersionDir.listFiles()?.filter { it.isFile } ?: emptyList()
        if (files.isEmpty()) {
            throw GradleException(
                "暂存目录下没有找到制品: ${localVersionDir.absolutePath}\n" +
                    "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
            )
        }

        for (file in files) {
            val remoteUrl = "${remoteBasePath}/${file.name}"
            uploadFile(file, remoteUrl, username, password)
        }

        // 上传/更新 maven-metadata.xml（在 mcVersion 层级）
        uploadMavenMetadata(repoUrl, groupPath, artifactId, mcVer, ver, username, password)

        logger.lifecycle("✓ Maven 发布完成: ${artifactId}-${ver} (MC ${mcVer})")
    }

    private fun uploadFile(file: File, url: String, username: String, password: String) {
        logger.lifecycle("  上传: ${file.name}")
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Authorization", basicAuth(username, password))
        conn.setRequestProperty("Content-Type", guessContentType(file.name))
        conn.setRequestProperty("Content-Length", file.length().toString())
        conn.doOutput = true
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        try {
            file.inputStream().use { input -> conn.outputStream.use { output -> input.copyTo(output) } }
            val code = conn.responseCode
            if (code !in listOf(200, 201, 204)) {
                val error = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: "unknown error"
                } catch (_: Exception) { "unknown error" }
                throw GradleException(
                    "Maven 上传失败 / Maven upload failed ($code): $url\n  $error\n" +
                        "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadBytes(data: ByteArray, url: String, contentType: String, username: String, password: String) {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Authorization", basicAuth(username, password))
        conn.setRequestProperty("Content-Type", contentType)
        conn.setRequestProperty("Content-Length", data.size.toString())
        conn.doOutput = true
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        try {
            conn.outputStream.use { it.write(data) }
            val code = conn.responseCode
            if (code !in listOf(200, 201, 204)) {
                val error = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: "unknown error"
                } catch (_: Exception) { "unknown error" }
                throw GradleException(
                    "Maven 上传失败 / Maven upload failed ($code): $url\n  $error\n" +
                        "详情请参阅 / See: ${VersionChecker.DOCS_URL}"
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadMavenMetadata(
        repoUrl: String, groupPath: String, artifactId: String,
        mcVersion: String, version: String,
        username: String, password: String
    ) {
        val metadataUrl = "${repoUrl}/${groupPath}/${artifactId}/${mcVersion}/maven-metadata.xml"

        // 尝试下载已有的 metadata
        val existingVersions = mutableListOf<String>()
        try {
            val conn = URI(metadataUrl).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", basicAuth(username, password))
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            try {
                if (conn.responseCode == 200) {
                    val xml = conn.inputStream.bufferedReader().readText()
                    Regex("""<version>([^<]+)</version>""").findAll(xml).forEach {
                        existingVersions += it.groupValues[1]
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            // 首次发布，metadata 不存在
        }

        if (!existingVersions.contains(version)) {
            existingVersions += version
        }

        val timestamp = java.text.SimpleDateFormat("yyyyMMddHHmmss").apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val metadata = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<metadata>")
            appendLine("  <groupId>${projectGroup.get()}</groupId>")
            appendLine("  <artifactId>${artifactId}</artifactId>")
            appendLine("  <versioning>")
            appendLine("    <latest>${version}</latest>")
            appendLine("    <release>${version}</release>")
            appendLine("    <versions>")
            for (v in existingVersions) {
                appendLine("      <version>${v}</version>")
            }
            appendLine("    </versions>")
            appendLine("    <lastUpdated>${timestamp}</lastUpdated>")
            appendLine("  </versioning>")
            appendLine("</metadata>")
        }

        val metadataBytes = metadata.toByteArray(Charsets.UTF_8)
        logger.lifecycle("  上传: maven-metadata.xml")
        uploadBytes(metadataBytes, metadataUrl, "application/xml", username, password)

        // checksums for metadata
        val sha1 = hash(metadataBytes, "SHA-1")
        uploadBytes(sha1.toByteArray(), "${metadataUrl}.sha1", "text/plain", username, password)
        val md5 = hash(metadataBytes, "MD5")
        uploadBytes(md5.toByteArray(), "${metadataUrl}.md5", "text/plain", username, password)

        logger.lifecycle("  ✓ maven-metadata.xml 已更新")
    }

    private fun hash(data: ByteArray, algorithm: String): String {
        val digest = java.security.MessageDigest.getInstance(algorithm)
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun guessContentType(name: String): String = when {
        name.endsWith(".jar") -> "application/java-archive"
        name.endsWith(".pom") || name.endsWith(".xml") -> "application/xml"
        name.endsWith(".module") -> "application/json"
        else -> "application/octet-stream"
    }

    private fun basicAuth(username: String, password: String): String {
        val credentials = "$username:$password"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
    }
}
