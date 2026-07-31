package com.datasandbox.pro.core

/**
 * Basic grid & dependency graph scaffolding.
 * This file provides lightweight types that will be used by the Grid Engine
 * and dependency tracker. Implementations are intentionally minimal to avoid
 * introducing heavy dependencies at this stage.
 */

// NOTE: CellAddress / CellValue / Cell now live in CellTypes.kt.
// (Previously duplicated here, which caused Kotlin "Redeclaration" compile errors.)

class DependencyGraph(private val edges: MutableMap<CellAddress, MutableSet<CellAddress>> = mutableMapOf()) {
    fun addEdge(from: CellAddress, to: CellAddress) {
        edges.computeIfAbsent(from) { mutableSetOf() }.add(to)
    }

    /**
     * Kahn's algorithm - returns topological order of nodes that have entries in the graph.
     * Empty list when nothing to sort.
     */
    fun topologicalSort(): List<CellAddress> {
        val inDegree = mutableMapOf<CellAddress, Int>()
        edges.keys.forEach { inDegree[it] = 0 }
        edges.forEach { (u, vs) ->
            vs.forEach { v -> inDegree[v] = (inDegree[v] ?: 0) + 1 }
        }
        val queue = ArrayDeque<CellAddress>()
        inDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }

        val result = mutableListOf<CellAddress>()
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            result.add(n)
            edges[n]?.forEach { m ->
                inDegree[m] = inDegree[m]!! - 1
                if (inDegree[m] == 0) queue.add(m)
            }
        }
        // If result size < nodes, a cycle exists; caller may detect that separately
        return result
    }

    fun detectCycle(): Boolean {
        val sorted = topologicalSort()
        val totalNodes = edges.keys.union(edges.values.flatten().toSet()).size
        return sorted.size < totalNodes
    }

    fun getDependencies(address: CellAddress): List<CellAddress> {
        return edges[address]?.toList() ?: emptyList()
    }
}
