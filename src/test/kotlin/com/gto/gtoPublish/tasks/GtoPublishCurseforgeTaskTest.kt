package com.gto.gtoPublish.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GtoPublishCurseforgeTaskTest {

    @Test
    fun `metadata omits relations when none configured`() {
        val metadata = GtoPublishCurseforgeTask.buildMetadata("1.0.0", "a.jar", listOf(1, 2), "release", emptyList())
        assertFalse(metadata.containsKey("relations"))
        assertEquals(listOf(1, 2), metadata["gameVersions"])
    }

    @Test
    fun `metadata carries relations as projects list`() {
        val relations = listOf(mapOf("slug" to "gtoutils", "type" to "requiredDependency"))
        val metadata = GtoPublishCurseforgeTask.buildMetadata("1.0.0", "a.jar", listOf(1), "release", relations)
        assertEquals(mapOf("projects" to relations), metadata["relations"])
    }
}
