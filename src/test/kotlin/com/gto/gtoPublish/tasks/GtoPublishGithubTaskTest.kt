package com.gto.gtoPublish.tasks

import org.gradle.testfixtures.ProjectBuilder
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GtoPublishGithubTaskTest {

    @BeforeTest
    fun resetHttp() {
        FakeHttps.install()
        FakeHttps.reset()
    }

    @Test
    fun `github release tag includes loader minecraft version and mod version`() {
        val project = ProjectBuilder.builder().build()
        val libsDir = project.layout.buildDirectory.dir("libs").get().asFile
        libsDir.mkdirs()
        libsDir.resolve("registrylib-neoforge-26.1.2-4.2.0.jar").writeText("jar")

        val task = project.tasks.register("publishGithub", GtoPublishGithubTask::class.java).get().apply {
            projectVersion.set("4.2.0")
            githubToken.set("ghp_test")
            githubRepo.set("owner/repo")
            mavenRepoUrl.set("https://maven.example/releases")
            projectGroup.set("com.gto")
            archivesName.set("registrylib-neoforge-26.1.2")
            skipMavenConsistencyCheck.set(true)
            this.libsDir = libsDir
        }

        task.publish()

        val checkedRelease = FakeHttps.connections.single {
            it.url.host == "api.github.com" && it.url.path.contains("/releases/tags/")
        }
        assertEquals(
            "https://api.github.com/repos/owner/repo/releases/tags/neoforge-26.1.2-4.2.0",
            checkedRelease.url.toString()
        )

        val createdRelease = FakeHttps.connections.single {
            it.url.host == "api.github.com" && it.url.path == "/repos/owner/repo/releases"
        }
        assertTrue(createdRelease.body.contains("\"tag_name\":\"neoforge-26.1.2-4.2.0\""))
        assertTrue(createdRelease.body.contains("\"name\":\"neoforge-26.1.2-4.2.0\""))
        assertTrue(createdRelease.body.contains("\"body\":\"Release neoforge-26.1.2-4.2.0 (release)\""))
    }
}

private object FakeHttps {
    private var installed = false
    val connections = mutableListOf<FakeHttpURLConnection>()

    fun install() {
        if (installed) return
        URL.setURLStreamHandlerFactory(FakeHttpsHandlerFactory)
        installed = true
    }

    fun reset() {
        connections.clear()
    }

    private object FakeHttpsHandlerFactory : URLStreamHandlerFactory {
        override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
            return if (protocol == "https") {
                object : URLStreamHandler() {
                    override fun openConnection(url: URL): URLConnection {
                        return FakeHttpURLConnection(url).also { connections += it }
                    }
                }
            } else {
                null
            }
        }
    }
}

private class FakeHttpURLConnection(url: URL) : HttpURLConnection(url) {
    private val output = ByteArrayOutputStream()

    val body: String
        get() = output.toString(Charsets.UTF_8.name())

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun getOutputStream(): ByteArrayOutputStream = output

    override fun getInputStream(): InputStream {
        val response = when {
            url.host == "api.github.com" && url.path == "/repos/owner/repo/releases" ->
                """{"upload_url":"https://uploads.github.com/repos/owner/repo/releases/1/assets{?name,label}"}"""
            else -> ""
        }
        return response.byteInputStream()
    }

    override fun getResponseCode(): Int {
        return when {
            url.host == "api.github.com" && url.path.contains("/releases/tags/") -> 404
            url.host == "api.github.com" && url.path == "/repos/owner/repo/releases" -> 201
            url.host == "uploads.github.com" -> 201
            else -> 200
        }
    }
}
