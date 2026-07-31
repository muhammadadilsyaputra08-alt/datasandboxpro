package com.datasandbox.pro.storage

import java.io.File
import java.io.FileWriter

/**
 * Lightweight Document storage implemented as a directory of CSV files.
 *
 * This is a pragmatic, dependency-free placeholder for the planned .dsb (SQLite)
 * implementation. Each table is stored as a CSV file under the document directory
 * with the first line as a header containing column names.
 *
 * Note: This implementation is intentionally simple and suitable for local
 * testing and early development. We will replace or augment this with an
 * actual SQLite-backed .dsb document layer later per the blueprint.
 */
class Document(private val directory: File) {

    init {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    fun listTables(): List<String> = directory.listFiles()
        ?.filter { it.isFile && it.extension == "csv" }
        ?.map { it.nameWithoutExtension }
        ?: emptyList()

    fun createTable(tableName: String, columns: List<String>) {
        val file = File(directory, "$tableName.csv")
        if (file.exists()) return
        FileWriter(file).use { w ->
            w.append(columns.joinToString(",") { escapeCsv(it) })
            w.append('\n')
        }
    }

    fun insertRow(tableName: String, row: Map<String, String>) {
        val file = File(directory, "$tableName.csv")
        if (!file.exists()) throw IllegalArgumentException("Table $tableName does not exist")
        val header = file.readLines().firstOrNull()?.split(',')?.map { unescapeCsv(it) } ?: emptyList()
        val values = header.map { k -> row[k] ?: "" }
        FileWriter(file, true).use { w ->
            w.append(values.joinToString(",") { escapeCsv(it) })
            w.append('\n')
        }
    }

    fun readAllRows(tableName: String): List<Map<String, String>> {
        val file = File(directory, "$tableName.csv")
        if (!file.exists()) throw IllegalArgumentException("Table $tableName does not exist")
        val lines = file.readLines()
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split(',').map { unescapeCsv(it) }
        return lines.drop(1).map { line ->
            val parts = splitCsv(line)
            header.mapIndexed { idx, col -> col to (parts.getOrNull(idx) ?: "") }.toMap()
        }
    }

    fun queryTable(tableName: String, predicate: ((Map<String, String>) -> Boolean)? = null, limit: Int = 100): List<Map<String, String>> {
        val rows = readAllRows(tableName)
        val filtered = predicate?.let { rows.filter(it) } ?: rows
        return if (filtered.size <= limit) filtered else filtered.subList(0, limit)
    }

    private fun escapeCsv(s: String): String {
        // Very small CSV escaper: wrap fields containing comma/quote/newline in quotes and escape quotes with double quotes
        return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s
    }

    private fun unescapeCsv(s: String): String {
        val t = s.trim()
        return if (t.startsWith('"') && t.endsWith('"')) {
            t.substring(1, t.length - 1).replace("\"\"", "\"")
        } else t
    }

    private fun splitCsv(line: String): List<String> {
        val res = mutableListOf<String>()
        var i = 0
        val n = line.length
        while (i < n) {
            if (line[i] == '"') {
                // quoted
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
