package com.datasandbox.pro.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Opens a portable ".dsb" document: a plain SQLite database that stores
 * tables, columns, rows (as JSON), dashboards, and the AI pattern-weights
 * cache. Any file produced here can be inspected with a generic SQLite
 * browser, which is the whole point of the portable-document format.
 */
class DsbDatabase(context: Context, dbName: String = "datasandbox.dsb") :
    SQLiteOpenHelper(context, dbName, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE _metadata (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE tables (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                description TEXT DEFAULT '',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE columns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_id INTEGER NOT NULL REFERENCES tables(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                col_index INTEGER NOT NULL,
                type TEXT NOT NULL DEFAULT 'TEXT',
                formula TEXT,
                is_primary_key INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE rows (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_id INTEGER NOT NULL REFERENCES tables(id) ON DELETE CASCADE,
                row_index INTEGER NOT NULL,
                data_json TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE dashboards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                widgets_json TEXT NOT NULL DEFAULT '[]'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pattern_weights (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pattern_signature TEXT UNIQUE NOT NULL,
                tool_sequence TEXT NOT NULL DEFAULT '[]',
                success_score REAL DEFAULT 0,
                execution_time_ms INTEGER DEFAULT 0,
                frequency_used INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_columns_table ON columns(table_id)")
        db.execSQL("CREATE INDEX idx_rows_table ON rows(table_id, row_index)")

        db.execSQL(
            "INSERT INTO _metadata(key, value) VALUES ('format_version', '1'), ('app', 'DataSandbox Pro')"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive migrations go here as the schema evolves.
    }

    companion object {
        const val DB_VERSION = 1
    }
}
