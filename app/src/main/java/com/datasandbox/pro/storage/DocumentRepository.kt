package com.datasandbox.pro.storage

import android.content.Context

/**
 * High-level DocumentRepository to manage opening/creating .dsb documents.
 * It simplifies obtaining a SqliteDocument instance and basic helpers.
 */
class DocumentRepository(private val context: Context) {
    fun openDocument(name: String): SqliteDocument = SqliteDocument(context, name)

    fun createSampleSalesDocument(name: String): SqliteDocument {
        val doc = openDocument(name)
        doc.createTable("sales", listOf(ColumnSpec("date", "TEXT"), ColumnSpec("product", "TEXT"), ColumnSpec("quantity", "INTEGER"), ColumnSpec("revenue", "REAL")))
        doc.insertRow("sales", mapOf("date" to "2026-07-01", "product" to "Widget", "quantity" to "3", "revenue" to "150.0"))
        doc.insertRow("sales", mapOf("date" to "2026-07-02", "product" to "Gadget", "quantity" to "1", "revenue" to "99.0"))
        return doc
    }
}
