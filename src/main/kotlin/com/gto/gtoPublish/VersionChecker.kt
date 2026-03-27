package com.gto.gtoPublish

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

object VersionChecker {

    const val DOCS_URL = "https://github.com/GregTech-Odyssey/GradlePublishPlugin"

    /**
     * 版本格式: {mcVersion}-{modVersion}(-{releaseType})?
     * mcVersion: 如 26.1, 1.12.2, 1.7.10 等 (至少两段数字)
     * modVersion: x.x.x (恰好三段数字)
     * releaseType: alpha / beta / release / 空
     *
     * 示例: 26.1-1.0.0-release, 1.12.2-2.3.1-beta, 26.1-1.0.0
     */
    private val VERSION_REGEX = Regex("""^(.+)-(\d+\.\d+\.\d+)(-(alpha|beta|release))?$""")
    private val MC_VERSION_REGEX = Regex("""^\d+(\.\d+)+$""")

    fun checkVersionFormat(version: String) {
        val match = VERSION_REGEX.matchEntire(version)
        if (match == null) {
            throw GradleException(
                "mod_version '${version}' 不是合法的版本号格式 / Invalid version format\n" +
                    "要求格式 / Required: {mcVersion}-{modVersion}[-alpha|-beta|-release]\n" +
                    "示例 / Examples: 26.1-1.0.0-release, 1.12.2-2.3.1-beta, 26.1-1.0.0\n" +
                    "详情请参阅 / See: $DOCS_URL"
            )
        }
        val mcVersion = match.groupValues[1]
        if (!mcVersion.matches(MC_VERSION_REGEX)) {
            throw GradleException(
                "MC 版本号 '${mcVersion}' 格式不合法 / Invalid Minecraft version format\n" +
                    "要求至少两段数字，如 26.1, 1.12.2, 1.7.10\n" +
                    "详情请参阅 / See: $DOCS_URL"
            )
        }
    }

    /** 从版本号中提取 MC 版本: 26.1-1.0.0-beta → 26.1 */
    fun parseMcVersion(version: String): String {
        return VERSION_REGEX.matchEntire(version)?.groupValues?.get(1) ?: version.substringBefore('-')
    }

    /** 从版本号中提取 mod 版本: 26.1-1.0.0-beta → 1.0.0 */
    fun parseModVersion(version: String): String {
        return VERSION_REGEX.matchEntire(version)?.groupValues?.get(2) ?: version
    }

    /**
     * 从版本号中解析发布类型: alpha / beta / release
     * 无后缀视为 release
     */
    fun parseReleaseType(version: String): String {
        val match = VERSION_REGEX.matchEntire(version) ?: return "release"
        val type = match.groupValues[4]
        return if (type.isNotEmpty()) type else "release"
    }

    /**
     * 获取显示版本号：去除 -release 后缀
     * 26.1-1.0.0-release → 26.1-1.0.0
     * 26.1-1.0.0-beta    → 26.1-1.0.0-beta
     * 26.1-1.0.0         → 26.1-1.0.0
     */
    fun displayVersion(version: String): String {
        return version.removeSuffix("-release")
    }

    /**
     * 通过 CurseForge API 验证 MC 版本号是否有效。
     * 返回该版本在 CurseForge 上传 API 中对应的 game version ID。
     */
    fun fetchCurseForgeMinecraftVersionId(mcVersion: String, logger: Logger): Int {
        val apiUrl = "https://api.curseforge.com/v1/minecraft/version"
        logger.lifecycle("  正在从 CurseForge API 验证 MC 版本: $mcVersion ...")
        val conn = URI(apiUrl).toURL().openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "GtoPublishPlugin")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            if (conn.responseCode != 200) {
                throw GradleException(
                    "CurseForge MC 版本 API 请求失败 / CurseForge MC version API failed (${conn.responseCode})\n" +
                        "详情请参阅 / See: $DOCS_URL"
                )
            }
            val json = conn.inputStream.bufferedReader().readText()
            // 响应格式: {"data":[{"id":101,"gameVersionId":15933,"versionString":"26.1",...}, ...]}
            val dataRegex = Regex(""""versionString"\s*:\s*"([^"]+)"[^}]*?"gameVersionId"\s*:\s*(\d+)""")
            // 也尝试反向字段顺序
            val dataRegex2 = Regex(""""gameVersionId"\s*:\s*(\d+)[^}]*?"versionString"\s*:\s*"([^"]+)"""")

            for (match in dataRegex.findAll(json)) {
                if (match.groupValues[1] == mcVersion) {
                    val gameVersionId = match.groupValues[2].toInt()
                    logger.lifecycle("  ✓ MC 版本 $mcVersion 有效 (CurseForge gameVersionId: $gameVersionId)")
                    return gameVersionId
                }
            }
            for (match in dataRegex2.findAll(json)) {
                if (match.groupValues[2] == mcVersion) {
                    val gameVersionId = match.groupValues[1].toInt()
                    logger.lifecycle("  ✓ MC 版本 $mcVersion 有效 (CurseForge gameVersionId: $gameVersionId)")
                    return gameVersionId
                }
            }

            throw GradleException(
                "MC 版本 '$mcVersion' 在 CurseForge 上不存在 / MC version not found on CurseForge\n" +
                    "请检查 mod_version 中的 MC 版本号是否正确。\n" +
                    "详情请参阅 / See: $DOCS_URL"
            )
        } finally {
            conn.disconnect()
        }
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
                    throw GradleException("Maven 仓库已存在版本 '${version}' / Version already exists in Maven\n请先修改 gradle.properties 中的 mod_version 再发布。\n详情请参阅 / See: $DOCS_URL")
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
                    throw GradleException("GitHub Release '${version}' 已存在 / Release already exists\n请先修改 gradle.properties 中的 mod_version 再发布。\n详情请参阅 / See: $DOCS_URL")
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
                        "Maven 仓库中不存在版本 '${version}' 的制品 / Artifact not found in Maven\n" +
                            "  URL: $jarUrl\n" +
                            "  请先执行 gtoPublishMaven 将制品发布到 Maven 后，再发布到其他平台。\n" +
                            "  详情请参阅 / See: $DOCS_URL"
                    )
                }
            } finally {
                headConn.disconnect()
            }
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            throw GradleException("无法连接 Maven 仓库校验制品 / Cannot connect to Maven: ${e.message}\n详情请参阅 / See: $DOCS_URL")
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
            throw GradleException("无法从 Maven 仓库下载 SHA-1 校验信息 / Failed to download SHA-1: ${e.message}\n详情请参阅 / See: $DOCS_URL")
        }

        // 3. 计算本地 JAR 的 SHA-1
        val localSha1 = sha1(localJar)
        logger.lifecycle("  本地  SHA-1: $localSha1")
        logger.lifecycle("  Maven SHA-1: $remoteSha1")

        if (!localSha1.equals(remoteSha1, ignoreCase = true)) {
            throw GradleException(
                "本地 JAR 与 Maven 仓库中的制品 SHA-1 不一致！ / SHA-1 mismatch\n" +
                    "  本地 / Local:  $localSha1\n" +
                    "  Maven:         $remoteSha1\n" +
                    "  请确保使用与 Maven 发布时相同的构建产物。\n" +
                    "  详情请参阅 / See: $DOCS_URL"
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

    /**
     * 读取插件自身版本号（从构建时生成的资源文件）
     */
    fun getPluginVersion(): String? {
        return VersionChecker::class.java.classLoader
            .getResourceAsStream("gto-publish-version.properties")
            ?.bufferedReader()?.use { reader ->
                val props = java.util.Properties()
                props.load(reader)
                props.getProperty("version")
            }
    }

    /**
     * 从 Maven 仓库的 maven-metadata.xml 获取插件最新版本号，
     * 与当前版本比较，若不是最新则抛出异常，阻止所有插件功能。
     */
    fun checkPluginUpdate(
        repoUrl: String,
        group: String,
        artifactId: String,
        currentVersion: String,
        logger: Logger
    ) {
        val groupPath = group.replace('.', '/')
        val metadataUrl = "${repoUrl}/${groupPath}/${artifactId}/maven-metadata.xml"
        try {
            val conn = URI(metadataUrl).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            try {
                if (conn.responseCode != 200) return
                val xml = conn.inputStream.bufferedReader().readText()
                // 解析 <release> 或 <latest> 标签
                val latestVersion = Regex("""<release>([^<]+)</release>""").find(xml)?.groupValues?.get(1)
                    ?: Regex("""<latest>([^<]+)</latest>""").find(xml)?.groupValues?.get(1)
                    ?: return
                if (latestVersion != currentVersion) {
                    throw GradleException(
                        "\n" +
                            "╔══════════════════════════════════════════════════════════════╗\n" +
                            "║       GTO Publish Plugin — 版本过期 / Outdated Version       ║\n" +
                            "╠══════════════════════════════════════════════════════════════╣\n" +
                            "║                                                              ║\n" +
                            "║  当前版本 / Current : $currentVersion\n" +
                            "║  最新版本 / Latest  : $latestVersion\n" +
                            "║                                                              ║\n" +
                            "║  所有发布功能已禁用，请先升级插件。                          ║\n" +
                            "║  All publish features are disabled. Please upgrade first.    ║\n" +
                            "║                                                              ║\n" +
                            "║  修改 build.gradle / Update build.gradle:                    ║\n" +
                            "║  id 'com.gto.gtopublishgradleplugin' version '$latestVersion'\n" +
                            "║                                                              ║\n" +
                            "║  文档 / Docs: $DOCS_URL\n" +
                            "║                                                              ║\n" +
                            "╚══════════════════════════════════════════════════════════════╝"
                    )
                }
                logger.lifecycle(
                    "✓ GTO Publish Plugin 版本检查通过 / Version check passed ($currentVersion)"
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: GradleException) {
            throw e
        } catch (_: Exception) {
            // 网络不可用时静默跳过，不影响正常构建
        }
    }
}
