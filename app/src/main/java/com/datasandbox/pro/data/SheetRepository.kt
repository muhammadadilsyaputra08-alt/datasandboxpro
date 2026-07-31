package com.datasandbox.pro.data

import android.content.ContentValues
import android.content.Context
import com.datasandbox.pro.engine.DependencyGraph
import com.datasandbox.pro.engine.EvaluationContext
import com.datasandbox.pro.engine.FormulaEngine
import com.datasandbox.pro.model.Cell
import com.datasandbox.pro.model.CellAddress
import com.datasandbox.pro.model.CellValue
import com.datasandbox.pro.model.ColumnDef
import com.datasandbox.pro.model.ColumnType
import org.json.JSONObject

/**
 * In-memory grid backed by the .dsb SQLite file. Handles CRUD, formula
 * recalculation via [DependencyGraph] + [FormulaEngine], and simple
 * import/export helpers.
 */
class SheetRepository(context: Context) {

    private val helper = DsbDatabase(context)
    private val engine = FormulaEngine()
    private val graph = DependencyGraph()

    // table name -> row -> column letter -> Cell
    private val cache = LinkedHashMap<String, LinkedHashMap<Int, MutableMap<String, Cell>>>()

    fun ensureDefaultTable(name: String, columnNames: List<String>) {
        val db = helper.writableDatabase
        val exists = db.rawQuery("SELECT id FROM tables WHERE name = ?", arrayOf(name)).use { it.moveToFirst() }
        if (exists) return

        val tableId = db.insert("tables", null, ContentValues().apply {
            put("name", name)
        })
        columnNames.forEachIndexed { idx, colName ->
            db.insert("columns", null, ContentValues().apply {
                put("table_id", tableId)
                put("name", colName)
                put("col_index", idx)
                put("type", ColumnType.TEXT.name)
            })
        }
    }

    fun setCell(table: String, row: Int, column: String, rawInput: String) {
        val isFormula = rawInput.startsWith("=")
        val cell = Cell(
            address = CellAddress(table, row, column),
            rawInput = rawInput,
            formula = if (isFormula) rawInput else null
        )

        val tableRows = cache.getOrPut(table) { LinkedHashMap() }
        val rowMap = tableRows.getOrPut(row) { mutableMapOf() }
        rowMap[column] = cell

        recalculate(cell.address)
        persistRow(table, row, rowMap)
    }

    fun getCell(table: String, row: Int, column: String): Cell? = cache[table]?.get(row)?.get(column)

    fun getValue(address: CellAddress): CellValue = getCell(address.table, address.row, address.column)?.value ?: CellValue.Empty

    fun rowsFor(table: String): List<Int> = cache[table]?.keys?.sorted() ?: emptyList()

    private fun recalculate(changed: CellAddress) {
        val cell = cache[changed.table]?.get(changed.row)?.get(changed.column) ?: return
        val ctx = context(changed.table)

        if (cell.formula != null) {
            val deps = engine.extractDependencies(cell.formula, changed.table)
            graph.setDependencies(changed, deps)
        } else {
            graph.setDependencies(changed, emptyList())
            cell.value = coerceLiteral(cell.rawInput)
        }

        val order = try {
            graph.topologicalOrderFrom(changed)
        } catch (e: Exception) {
            cell.value = CellValue.Err("CIRCULAR")
            return
        }

        order.forEach { addr ->
            val c = cache[addr.table]?.get(addr.row)?.get(addr.column) ?: return@forEach
            if (c.formula != null) {
                c.value = engine.evaluate(c.formula, context(addr.table))
            }
        }
    }

    private fun coerceLiteral(raw: String): CellValue = when {
        raw.isEmpty() -> CellValue.Empty
        raw.toDoubleOrNull() != null -> CellValue.Num(raw.toDouble())
        raw.equals("TRUE", true) -> CellValue.Bool(true)
        raw.equals("FALSE", true) -> CellValue.Bool(false)
        else -> CellValue.Str(raw)
    }

    private fun context(table: String) = object : EvaluationContext {
        override val currentTable = table
        override fun cellValue(address: CellAddress) = getValue(address)
        override fun columnValues(t: String, column: String): List<CellValue> =
            rowsFor(t).mapNotNull { r -> getCell(t, r, column)?.value }
    }

    private fun persistRow(table: String, row: Int, rowMap: Map<String, Cell>) {
        val db = helper.writableDatabase
        val tableId = db.rawQuery("SELECT id FROM tables WHERE name = ?", arrayOf(table))
            .use { if (it.moveToFirst()) it.getLong(0) else return }

        val json = JSONObject()
        rowMap.forEach { (col, cell) -> json.put(col, cell.rawInput) }

        val existing = db.rawQuery(
            "SELECT id FROM rows WHERE table_id = ? AND row_index = ?",
            arrayOf(tableId.toString(), row.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

        val values = ContentValues().apply {
            put("table_id", tableId)
            put("row_index", row)
            put("data_json", json.toString())
        }
        if (existing != null) {
            db.update("rows", values, "id = ?", arrayOf(existing.toString()))
        } else {
            db.insert("rows", null, values)
        }
    }

    fun close() = helper.close()
}
