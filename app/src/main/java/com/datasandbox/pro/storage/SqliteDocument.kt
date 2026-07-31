package com.datasandbox.pro.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Sqlite-backed Document implementation (.dsb file) using Android's SQLite APIs.
 *
 * This class wraps a SQLite database stored in the app's files directory. The
 * initial schema is read from the dsb_schema.sql asset that was added earlier.
 *
 * Usage (Android):
 *  val doc = SqliteDocument(context, "mydoc.dsb")
 *  doc.createTable("sales", listOf(ColumnSpec("id","TEXT"), ColumnSpec("amount","REAL")))
 *  doc.insertRow("sales", mapOf("id" to "1", "amount" to "100"))
 *  val rows = doc.queryTable("sales")
 */

data class ColumnSpec(val name: String, val type: String = "TEXT")

class SqliteDocument(private val context: Context, private val dbName: String) : SQLiteOpenHelper(
    context,
    dbName,
    null,
    DB_VERSION
) {
    companion object {
        private const val DB_VERSION = 1
        private const val SCHEMA_ASSET_PATH = "dsb_schema.sql"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Read schema SQL from assets and execute statements
        try {
            context.assets.open(SCHEMA_ASSET_PATH).use { ins ->
                BufferedReader(InputStreamReader(ins)).use { reader ->
                    val sql = StringBuilder()
                    reader.lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("--")) return@forEach
                        sql.append(line).append('\n')
                    }
                    // Split statements by semicolon
                    sql.toString().splitToSequence(';').map { it.trim() }.filter { it.isNotEmpty() }
                        .forEach { stmt ->
                            db.execSQL(stmt)
                        }
                }
            }
        } catch (ex: Exception) {
            // If the asset isn't present or there's an error, still allow DB creation to continue.
            ex.printStackTrace()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple migration strategy: for now, no-op. Implement migrations here.
    }

    fun createTable(tableName: String, columns: List<ColumnSpec>) {
        val cols = columns.joinToString(",") { "\"${it.name}\" ${it.type}" }
        val sql = "CREATE TABLE IF NOT EXISTS \"$tableName\" (id INTEGER PRIMARY KEY AUTOINCREMENT, $cols)"
        writableDatabase.execSQL(sql)
    }

    fun insertRow(tableName: String, row: Map<String, String>) {
        val cv = ContentValues()
        row.forEach { (k, v) -> cv.put(k, v) }
        writableDatabase.insert(tableName, null, cv)
    }

    fun queryTable(tableName: String, whereClause: String? = null, whereArgs: Array<String>? = null, limit: Int = 100): List<Map<String, String>> {
        val db = readableDatabase
        val cursor: Cursor = db.query(tableName, null, whereClause, whereArgs, null, null, null, limit.toString())
        val res = mutableListOf<Map<String, String>>()
        cursor.use {
            val cols = cursor.columnNames
            while (cursor.moveToNext()) {
                val m = mutableMapOf<String, String>()
                for (c in cols) {
                    m[c] = cursor.getString(cursor.getColumnIndexOrThrow(c)) ?: ""
                }
                res.add(m)
            }
        }
        return res
    }

    fun exportDatabaseFile(destination: String) {
        // Copy DB from app's DB path to destination path. Caller must handle permissions.
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return
        dbFile.copyTo(java.io.File(destination), overwrite = true)
    }
}
