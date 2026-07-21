package com.gto.gtoPublish

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GtoPublishExtensionTest {

    @Test
    fun `both resolves to Client and Server`() {
        assertEquals(
            listOf("Client", "Server"),
            GtoPublishExtension.resolveEnvironmentVersions("both")
        )
        assertEquals(
            listOf("Client", "Server"),
            GtoPublishExtension.resolveEnvironmentVersions("client-server")
        )
        assertEquals(
            listOf("Client", "Server"),
            GtoPublishExtension.resolveEnvironmentVersions("ALL")
        )
    }

    @Test
    fun `client resolves to Client only`() {
        assertEquals(
            listOf("Client"),
            GtoPublishExtension.resolveEnvironmentVersions("client")
        )
        assertEquals(
            listOf("Client"),
            GtoPublishExtension.resolveEnvironmentVersions("client-only")
        )
    }

    @Test
    fun `server resolves to Server only`() {
        assertEquals(
            listOf("Server"),
            GtoPublishExtension.resolveEnvironmentVersions("server")
        )
        assertEquals(
            listOf("Server"),
            GtoPublishExtension.resolveEnvironmentVersions("server_only")
        )
    }

    @Test
    fun `invalid environment throws`() {
        assertFailsWith<GradleException> {
            GtoPublishExtension.resolveEnvironmentVersions("desktop")
        }
    }
}
