package com.datasandbox.pro.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DocumentTest {
    @Test
    fun testCreateInsertRead() {
        val dir = createTempDirectory().toFile()
        val doc = Document(dir)
        doc.createTable("people", listOf("id", "name", "age"))
        doc.insertRow("people", mapOf("id" to "1", "name" to "Alice", "age" to "30"))
        doc.insertRow("people", mapOf("id" to "2", "name" to "Bob", "age" to "25"))
        val rows = doc.readAllRows("people")
        assertEquals(2, rows.size)
        assertEquals("Alice", rows[0]["name"])
    }
}
