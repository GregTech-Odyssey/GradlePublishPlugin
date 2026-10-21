package com.gto.gtoPublish.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

    @Test
    fun `file id is read from upload response`() {
        assertEquals(9273000L, GtoPublishCurseforgeTask.parseFileId("""{"id":9273000}"""))
        assertNull(GtoPublishCurseforgeTask.parseFileId("""{"errorCode":1018}"""))
        assertNull(GtoPublishCurseforgeTask.parseFileId("not json"))
    }

    @Test
    fun `result carries what a pack reference needs`() {
        val result = GtoPublishCurseforgeTask.buildResult("1756992", 9273000L, "26.10.7", "a.jar", 10L, "ab")
        assertEquals(1756992L, result["projectId"])
        assertEquals(9273000L, result["fileId"])
        assertEquals("a.jar", result["fileName"])
        assertEquals("ab", result["sha1"])
    }
}
