package com.datasandbox.pro.storage

import java.io.File

/**
 * Very small CSV importer that writes into the Document storage. It assumes the
 * first line of the CSV file is a header row. This importer performs an
 * incremental import: it appends rows and does not attempt to deduplicate.
 */
object CsvImporter {
    fun import(file: File, document: Document, tableName: String) {
        val lines = file.readLines()
        if (lines.isEmpty()) return
        val header = lines.first().split(',').map { it.trim().trim('"') }
        document.createTable(tableName, header)
        lines.drop(1).forEach { line ->
            val values = splitCsv(line)
            val map = header.mapIndexed { idx, col -> col to (values.getOrNull(idx) ?: "") }.toMap()
            document.insertRow(tableName, map)
        }
    }

    private fun splitCsv(line: String): List<String> {
        val res = mutableListOf<String>()
        var i = 0
        val n = line.length
        while (i < n) {
            if (line[i] == '"') {
                val sb = StringBuilder()
                i++
                while (i < n) {
                    if (line[i] == '"') {
                        if (i + 1 < n && line[i + 1] == '"') {
                            sb.append('"')
                            i += 2
                        } else {
                            i++
                            break
                        }
                    } else {
                        sb.append(line[i])
                        i++
                    }
                }
                res.add(sb.toString())
                if (i < n && line[i] == ',') i++
            } else {
                val start = i
                while (i < n && line[i] != ',') i++
                res.add(line.substring(start, i))
                if (i < n && line[i] == ',') i++
            }
        }
        return res
    }
}
