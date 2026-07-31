package com.datasandbox.pro.core

/**
 * RecalcEngine: builds dependency graph from cell formulas and evaluates cells
 * in topological order. Cells that are errors or part of a cycle will be
 * returned as CellValue.Error.
 */

class RecalcEngine {
    fun recalc(cells: Map<CellAddress, Cell>): Map<CellAddress, Cell> {
        // Prepare adjacency: dependency -> set(dependents)
        val graph = DependencyGraph()
        // map of address string -> cell
        val addrToCell = cells
        // collect nodes
        addrToCell.keys.forEach { /* ensure presence in graph keys if needed */ }

        // parse formulas and build edges
        val formulaMap = mutableMapOf<CellAddress, String>()
        for ((addr, cell) in addrToCell) {
            if (!cell.formula.isNullOrBlank()) {
                formulaMap[addr] = cell.formula
                // extract dependencies from formula AST
                try {
                    val ast = parseFormula(cell.formula)
                    val deps = collectReferences(ast)
                    deps.forEach { depName ->
                        val depAddr = parseCellAddress(depName)
                        if (depAddr != null) graph.addEdge(depAddr, addr)
                    }
                } catch (ex: Exception) {
                    // ignore parse errors here; will surface during evaluation
                }
            }
        }

        // detect cycles
        val hasCycle = graph.detectCycle()
        val result = mutableMapOf<CellAddress, Cell>()
        // initial values: copy existing literal values
        val evaluatedValues = mutableMapOf<String, Double>()
        for ((addr, cell) in addrToCell) {
            when (val v = cell.value) {
                is CellValue.Number -> evaluatedValues[cellAddressToKey(addr)] = v.value
                is CellValue.Text -> {
                    // attempt numeric parse
                    val d = v.value.toDoubleOrNull()
                    if (d != null) evaluatedValues[cellAddressToKey(addr)] = d
                }
                else -> {}
            }
        }

        if (hasCycle) {
            // mark cells in cycle as error; for simplicity mark all formula cells as error
            for ((addr, cell) in addrToCell) {
                if (!cell.formula.isNullOrBlank()) {
                    result[addr] = cell.copy(value = CellValue.Error("Circular reference"))
                } else result[addr] = cell
            }
            return result
        }

        // topological order
        val order = graph.topologicalSort()
        // evaluate in order
        for (node in order) {
            val formula = formulaMap[node] ?: continue
            try {
                val ctx = EvaluationContext(values = evaluatedValues)
                val value = FormulaEvaluator.evaluate(formula, ctx)
                val newCell = addrToCell[node]?.copy(value = CellValue.Number(value)) ?: Cell(
                    address = node,
                    value = CellValue.Number(value)
                )
                result[node] = newCell
                evaluatedValues[cellAddressToKey(node)] = value
            } catch (ex: Exception) {
                result[node] = addrToCell[node]?.copy(value = CellValue.Error(ex.message ?: "error"))
            }
        }

        // include any non-formula cells unchanged
        for ((addr, cell) in addrToCell) {
            if (!result.containsKey(addr)) result[addr] = cell
        }

        return result
    }

    private fun cellAddressToKey(addr: CellAddress): String = (addr.column + addr.row.toString()).uppercase()

    private fun parseCellAddress(name: String): CellAddress? {
        val m = Regex("^([A-Z]+)([0-9]+)").find(name.uppercase()) ?: return null
        val col = m.groupValues[1]
        val row = m.groupValues[2].toInt()
        return CellAddress(sheet = "Sheet1", row = row, column = col)
    }

    private fun collectReferences(expr: Expr): List<String> {
        val res = mutableListOf<String>()
        fun walk(e: Expr) {
            when (e) {
                is Expr.LiteralNumber -> {}
                is Expr.Variable -> {
                    // treat variable that looks like cell address as reference
                    if (Regex("^[A-Z]+[0-9]+$").matches(e.name)) res.add(e.name)
                }
                is Expr.UnaryMinus -> walk(e.inner)
                is Expr.FunctionCall -> e.args.forEach { walk(it) }
                is Expr.Range -> {
                    // include both endpoints as references (recalc will read range values)
                    res.add(e.start)
                    res.add(e.end)
                }
            }
        }
        walk(expr)
        return res
    }
}
