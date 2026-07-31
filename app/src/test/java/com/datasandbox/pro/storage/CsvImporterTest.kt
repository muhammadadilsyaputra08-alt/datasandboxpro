package com.datasandbox.pro.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class CsvImporterTest {
    @Test
    fun testImportSimpleCsv() {
        val dir = createTempDirectory().toFile()
        val csvFile = File(dir, "sample.csv")
        csvFile.writeText("id,name,amount\n1,Alice,100\n2,Bob,200\n")
        val docDir = File(dir, "doc")
        val doc = Document(docDir)
        CsvImporter.import(csvFile, doc, "sales")
        val rows = doc.readAllRows("sales")
        assertEquals(2, rows.size)
        assertEquals("Bob", rows[1]["name"])
    }
}
