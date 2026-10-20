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

    @Test
    fun `relation types accept short and api names`() {
        assertEquals("requiredDependency", GtoPublishExtension.resolveRelationType("required"))
        assertEquals("requiredDependency", GtoPublishExtension.resolveRelationType("requiredDependency"))
        assertEquals("optionalDependency", GtoPublishExtension.resolveRelationType("Optional"))
        assertEquals("embeddedLibrary", GtoPublishExtension.resolveRelationType("embedded-library"))
        assertEquals("incompatible", GtoPublishExtension.resolveRelationType("incompatible"))
        assertEquals("tool", GtoPublishExtension.resolveRelationType("tool"))
    }

    @Test
    fun `invalid relation type throws`() {
        assertFailsWith<GradleException> {
            GtoPublishExtension.resolveRelationType("needed")
        }
    }

    @Test
    fun `relations resolve to sorted api payload`() {
        assertEquals(
            listOf(
                mapOf("slug" to "gtorender", "type" to "optionalDependency"),
                mapOf("slug" to "gtoutils", "type" to "requiredDependency")
            ),
            GtoPublishExtension.resolveRelations(mapOf("gtoutils" to "required", " gtorender " to "optional"))
        )
    }

    @Test
    fun `invalid relation slug throws`() {
        assertFailsWith<GradleException> {
            GtoPublishExtension.resolveRelations(mapOf("GTO Utils" to "required"))
        }
        assertFailsWith<GradleException> {
            GtoPublishExtension.resolveRelations(mapOf("" to "required"))
        }
    }

    @Test
    fun `relations property string parses`() {
        assertEquals(
            mapOf("gtoutils" to "required", "gtorender" to "optional"),
            GtoPublishExtension.parseRelations("gtoutils:required, gtorender=optional;")
        )
        assertEquals(emptyMap(), GtoPublishExtension.parseRelations(""))
        assertFailsWith<GradleException> {
            GtoPublishExtension.parseRelations("gtoutils")
        }
    }
}
