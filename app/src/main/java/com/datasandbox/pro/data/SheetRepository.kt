package com.datasandbox.pro.data

import android.content.Context
import com.datasandbox.pro.core.RecalcEngine
import com.datasandbox.pro.model.Cell
import com.datasandbox.pro.model.CellAddress
import com.datasandbox.pro.model.CellValue
import com.datasandbox.pro.model.ColumnType
import com.datasandbox.pro.storage.ColumnSpec
import com.datasandbox.pro.storage.DocumentRepository
import org.json.JSONObject

/**
 * SheetRepository implementation for the feature/formula-engine branch.
 *
 * Responsibilities:
 * - in-memory cache of cells (by table -> row -> column)
 * - persist rows into the SqliteDocument via DocumentRepository (basic)
 * - recalculate formulas using RecalcEngine
 *
 * NOTE: This implementation focuses on wiring the RecalcEngine to the
 * repository and providing a pragmatic persistence path via SqliteDocument.
 */
class SheetRepository(private val context: Context, private val docName: String = "default.dsb") {

    private val docRepo = DocumentRepository(context)
    private val document = docRepo.openDocument(docName)
    private val engine = RecalcEngine()

    // table -> rowIndex -> column -> Cell
    private val cache = linkedMapOf<String, LinkedHashMap<Int, MutableMap<String, Cell>>>()

    fun ensureDefaultTable(name: String, columnNames: List<String>) {
        // ensure table exists in SQLite document
        val cols = columnNames.map { ColumnSpec(it, "TEXT") }
        document.createTable(name, cols)
        // attempt to load any existing rows for this table into cache
        loadExistingData()
    }

    private fun loadExistingData() {
        try {
            // Build map of table id -> table name from the metadata 'tables' table if present
            val tablesMeta = try {
                document.queryTable("tables")
            } catch (ex: Exception) {
                emptyList<Map<String, String>>()
            }
            val idToName = tablesMeta.mapNotNull { m -> m["id"] to m["name"] }.toMap()

            // Read the generic 'rows' table (if present) which stores row_index and data_json
            val rowsMeta = try {
                document.queryTable("rows")
            } catch (ex: Exception) {
                emptyList<Map<String, String>>()
            }

            for (r in rowsMeta) {
                val tableId = r["table_id"] ?: continue
                val tableName = idToName[tableId] ?: continue
                val rowIndex = r["row_index"]?.toIntOrNull() ?: continue
                val dataJson = r["data_json"] ?: continue
                // parse JSON and populate cache
                try {
                    val json = JSONObject(dataJson)
                    val map = mutableMapOf<String, Cell>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val col = keys.next()
                        val raw = json.optString(col, "")
                        val c = Cell(
                            address = CellAddress(tableName, rowIndex, col),
                            rawInput = raw,
                            formula = if (raw.startsWith("=")) raw else null,
                            value = if (!raw.startsWith("=")) coerceLiteral(raw) else CellValue.Empty
                        )
                        map[col] = c
                    }
                    val tableRows = cache.getOrPut(tableName) { LinkedHashMap() }
                    tableRows[rowIndex] = map
                } catch (ex: Exception) {
                    // ignore malformed JSON for now
                }
            }
        } catch (ex: Exception) {
            // swallow
        }
    }

    fun setCell(table: String, row: Int, column: String, rawInput: String) {
        val isFormula = rawInput.startsWith("=")
        val cell = Cell(
            address = CellAddress(table, row, column),
            rawInput = rawInput,
            formula = if (isFormula) rawInput else null,
            value = if (!isFormula) coerceLiteral(rawInput) else CellValue.Empty
        )

        val tableRows = cache.getOrPut(table) { LinkedHashMap() }
        val rowMap = tableRows.getOrPut(row) { mutableMapOf() }
        rowMap[column] = cell

        recalculate(table, row)
        persistRow(table, row, rowMap)
    }

    fun getCell(table: String, row: Int, column: String): Cell? = cache[table]?.get(row)?.get(column)

    fun getValue(address: CellAddress): CellValue = getCell(address.table, address.row, address.column)?.value ?: CellValue.Empty

    fun rowsFor(table: String): List<Int> = cache[table]?.keys?.sorted() ?: emptyList()

    private fun recalculate(table: String, changedRow: Int) {
        // collect cells for this table into map expected by RecalcEngine
        val addrToCell = mutableMapOf<com.datasandbox.pro.core.CellAddress, com.datasandbox.pro.core.Cell>()
        val rows = cache[table] ?: return
        for ((rIdx, cols) in rows) {
            for ((col, cell) in cols) {
                // convert model.Cell to core.Cell used by RecalcEngine
                val coreAddr = com.datasandbox.pro.core.CellAddress(cell.address.table, cell.address.row, cell.address.column)
                val coreCell = com.datasandbox.pro.core.Cell(
                    address = coreAddr,
                    value = when (val v = cell.value) {
                        is CellValue.Num -> com.datasandbox.pro.core.CellValue.Number(v.value)
                        is CellValue.Str -> com.datasandbox.pro.core.CellValue.Text(v.value)
                        is CellValue.Bool -> com.datasandbox.pro.core.CellValue.Bool(v.value)
                        is CellValue.Err -> com.datasandbox.pro.core.CellValue.Error(v.message)
                        else -> com.datasandbox.pro.core.CellValue.Empty
                    },
                    formula = cell.formula,
                    dependencies = emptyList()
                )
                addrToCell[coreAddr] = coreCell
            }
        }

        // prepare tables map for evaluator (use cache keys as table names)
        val tablesMap = mutableMapOf<String, List<Map<String, String>>>()
        for (t in cache.keys) {
            try {
                val rowsList = document.queryTable(t)
                if (rowsList.isNotEmpty()) tablesMap[t] = rowsList
            } catch (ex: Exception) {
                // ignore
            }
        }

        val out = engine.recalc(addrToCell, tablesMap)
        // apply results back to cache
        out.forEach { (addr, coreCell) ->
            val t = addr.sheet
            val rowMap = cache[t]?.get(addr.row)
            if (rowMap != null) {
                val modelCell = rowMap[addr.column]
                if (modelCell != null) {
                    modelCell.value = when (val v = coreCell.value) {
                        is com.datasandbox.pro.core.CellValue.Number -> CellValue.Num(v.value)
                        is com.datasandbox.pro.core.CellValue.Text -> CellValue.Str(v.value)
                        is com.datasandbox.pro.core.CellValue.Bool -> CellValue.Bool(v.value)
                        is com.datasandbox.pro.core.CellValue.Error -> CellValue.Err(v.message)
                        else -> CellValue.Empty
                    }
                }
            }
        }
    }

    private fun coerceLiteral(raw: String) = when {
        raw.isEmpty() -> CellValue.Empty
        raw.toDoubleOrNull() != null -> CellValue.Num(raw.toDouble())
        raw.equals("TRUE", true) -> CellValue.Bool(true)
        raw.equals("FALSE", true) -> CellValue.Bool(false)
        else -> CellValue.Str(raw)
    }

    private fun persistRow(table: String, row: Int, rowMap: Map<String, Cell>) {
        try {
            // ensure table columns exist in SQLite doc
            val cols = rowMap.keys.map { ColumnSpec(it, "TEXT") }
            document.createTable(table, cols)
            // write row as JSON into a single 'data' column if table has that schema
            val json = JSONObject()
            rowMap.forEach { (col, cell) -> json.put(col, cell.rawInput) }
            // Attempt to insert into the table; if columns match, insertable; otherwise, insert into rows table
            try {
                val rowMapString = rowMap.mapValues { it.value.rawInput }
                document.insertRow(table, rowMapString)
            } catch (ex: Exception) {
                // fallback: insert into generic rows table
                val rowsJson = JSONObject().apply {
                    put("row_index", row)
                    put("data", json.toString())
                }
                document.insertRow("rows", mapOf("table_name" to table, "row_index" to row.toString(), "data_json" to json.toString()))
            }
        } catch (ex: Exception) {
            // swallow persistence errors for now
            ex.printStackTrace()
        }
    }

    fun close() {
        // nothing to close for now; SqliteDocument has close handled by Android framework when needed
    }
}
