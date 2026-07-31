package com.datasandbox.pro.engine

import com.datasandbox.pro.model.CellAddress

/**
 * Tracks which cells depend on which, and produces a safe evaluation order
 * via Kahn's algorithm. Detects circular references.
 */
class DependencyGraph {

    // edge: dependency -> set of cells that depend on it
    private val forward = HashMap<CellAddress, MutableSet<CellAddress>>()
    // edge: cell -> set of its dependencies
    private val backward = HashMap<CellAddress, MutableSet<CellAddress>>()

    fun setDependencies(cell: CellAddress, dependsOn: List<CellAddress>) {
        // clear old edges for this cell
        backward[cell]?.forEach { old -> forward[old]?.remove(cell) }
        backward[cell] = dependsOn.toMutableSet()
        dependsOn.forEach { dep ->
            forward.getOrPut(dep) { mutableSetOf() }.add(cell)
        }
    }

    fun dependentsOf(cell: CellAddress): Set<CellAddress> = forward[cell].orEmpty()

    fun dependenciesOf(cell: CellAddress): Set<CellAddress> = backward[cell].orEmpty()

    /** Returns cells in the order they must be recalculated, starting from [changed]. */
    fun topologicalOrderFrom(changed: CellAddress): List<CellAddress> {
        val affected = collectAffected(changed)
        val inDegree = HashMap<CellAddress, Int>()
        affected.forEach { cell ->
            inDegree[cell] = backward[cell].orEmpty().count { it in affected }
        }

        val queue = ArrayDeque(inDegree.filterValues { it == 0 }.keys)
        val order = mutableListOf<CellAddress>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            order += node
            forward[node].orEmpty().forEach { dependent ->
                if (dependent in affected) {
                    val newDeg = (inDegree[dependent] ?: 0) - 1
                    inDegree[dependent] = newDeg
                    if (newDeg == 0) queue.addLast(dependent)
                }
            }
        }

        if (order.size != affected.size) {
            throw CircularReferenceException("Circular reference detected involving $changed")
        }
        return order
    }

    fun detectCycle(startingAt: CellAddress): Boolean = try {
        topologicalOrderFrom(startingAt)
        false
    } catch (e: CircularReferenceException) {
        true
    }

    private fun collectAffected(start: CellAddress): Set<CellAddress> {
        val visited = mutableSetOf<CellAddress>()
        val stack = ArrayDeque<CellAddress>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (visited.add(node)) {
                forward[node].orEmpty().forEach { stack.addLast(it) }
            }
        }
        return visited
    }
}

class CircularReferenceException(message: String) : Exception(message)
