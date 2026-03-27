package com.gto.gtoPublish

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

object VersionChecker {

    private val VERSION_REGEX = Regex("""\d+\.\d+\.\d+(-(alpha|beta|release))?""")

    fun checkVersionFormat(version: String) {
        if (!version.matches(VERSION_REGEX)) {
            throw GradleException(
                "mod_version '${version}' 不是合法的版本号格式\n" +
                    "要求格式: x.x.x-alpha 或 x.x.x-beta 或 x.x.x-release 或 x.x.x"
            )
        }
    }

    /**
     * 从版本号中解析发布类型: alpha / beta / release
     * 无后缀的 x.x.x 视为 release
     */
    fun parseReleaseType(version: String): String {
        return if (version.contains('-')) version.substringAfterLast('-') else "release"
    }

    /**
     * 获取显示版本号：去除 -release 后缀
     * 1.0.0-release → 1.0.0
     * 1.0.0-alpha   → 1.0.0-alpha
     * 1.0.0-beta    → 1.0.0-beta
     * 1.0.0         → 1.0.0
     */
    fun displayVersion(version: String): String {
        return version.removeSuffix("-release")
    }

    fun checkMavenVersionNotExists(
        repoUrl: String,
        group: String,
        artifactId: String,
        version: String,
        logger: Logger
    ) {
        val groupPath = group.replace('.', '/')
        val pomUrl = "${repoUrl}/${groupPath}/${artifactId}/${version}/${artifactId}-${version}.pom"
        try {
            val conn = URI(pomUrl).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            try {
                if (conn.responseCode == 200) {
                    throw GradleException("Maven 仓库已存在版本 '${version}'\n请先修改 gradle.properties 中的 mod_version 再发布。")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            logger.warn("\u26A0 无法检查 Maven 版本: ${e.message}")
        }
    }

    fun checkGithubReleaseNotExists(
        repo: String,
        token: String,
        version: String,
        logger: Logger
    ) {
        try {
            val conn = URI("https://api.github.com/repos/${repo}/releases/tags/${version}")
                .toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            try {
                if (conn.responseCode == 200) {
                    throw GradleException("GitHub Release '${version}' 已存在\n请先修改 gradle.properties 中的 mod_version 再发布。")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            logger.warn("\u26A0 无法检查 GitHub Release: ${e.message}")
        }
    }

    /**
     * 校验本地 JAR 与 Maven 仓库中已发布的 JAR 的 SHA-1 一致。
     * 如果 Maven 上不存在该版本，则抛出异常（要求先发布到 Maven）。
     */
    fun requireMavenArtifactConsistent(
        repoUrl: String,
        group: String,
        artifactId: String,
        version: String,
        localJar: File,
        logger: Logger
    ) {
        val groupPath = group.replace('.', '/')
        val jarUrl = "${repoUrl}/${groupPath}/${artifactId}/${version}/${artifactId}-${version}.jar"
        val sha1Url = "${jarUrl}.sha1"

        // 1. 检查 Maven 上 JAR 是否存在
        logger.lifecycle("  校验 Maven 制品: $jarUrl")
        try {
            val headConn = URI(jarUrl).toURL().openConnection() as HttpURLConnection
            headConn.requestMethod = "HEAD"
            headConn.connectTimeout = 10000
            headConn.readTimeout = 10000
            try {
                if (headConn.responseCode != 200) {
                    throw GradleException(
                        "Maven 仓库中不存在版本 '${version}' 的制品\n" +
                            "  URL: $jarUrl\n" +
                            "  请先执行 gtoPublishMaven 将制品发布到 Maven 后，再发布到其他平台。"
                    )
                }
            } finally {
                headConn.disconnect()
            }
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            throw GradleException("无法连接 Maven 仓库校验制品: ${e.message}")
        }

        // 2. 下载远程 SHA-1
        val remoteSha1: String
        try {
            val sha1Conn = URI(sha1Url).toURL().openConnection() as HttpURLConnection
            sha1Conn.connectTimeout = 10000
            sha1Conn.readTimeout = 10000
            try {
                if (sha1Conn.responseCode != 200) {
                    // 没有 .sha1 文件，回退到下载完整 JAR 并计算 hash
                    logger.warn("  \u26A0 Maven 仓库未提供 .sha1 文件，回退到下载 JAR 比对")
                    sha1Conn.disconnect()
                    remoteSha1 = downloadAndHash(jarUrl)
                } else {
                    remoteSha1 = sha1Conn.inputStream.bufferedReader().readText().trim()
                        .split(Regex("\\s+"))[0] // 有些 sha1 文件格式为 "hash  filename"
                }
            } finally {
                sha1Conn.disconnect()
            }
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            throw GradleException("无法从 Maven 仓库下载 SHA-1 校验信息: ${e.message}")
        }

        // 3. 计算本地 JAR 的 SHA-1
        val localSha1 = sha1(localJar)
        logger.lifecycle("  本地  SHA-1: $localSha1")
        logger.lifecycle("  Maven SHA-1: $remoteSha1")

        if (!localSha1.equals(remoteSha1, ignoreCase = true)) {
            throw GradleException(
                "本地 JAR 与 Maven 仓库中的制品 SHA-1 不一致！\n" +
                    "  本地:  $localSha1\n" +
                    "  Maven: $remoteSha1\n" +
                    "  请确保使用与 Maven 发布时相同的构建产物。"
            )
        }
        logger.lifecycle("  \u2713 SHA-1 校验通过，本地制品与 Maven 一致")
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadAndHash(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        try {
            conn.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
        } finally {
            conn.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
