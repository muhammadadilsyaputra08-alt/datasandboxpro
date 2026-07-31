package com.datasandbox.pro.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyGraphTest {
    @Test
    fun topologicalSortDetectsCycle() {
        val g = DependencyGraph()
        val a = CellAddress("Sheet1", 1, "A")
        val b = CellAddress("Sheet1", 1, "B")
        val c = CellAddress("Sheet1", 1, "C")
        g.addEdge(a, b)
        g.addEdge(b, c)
        g.addEdge(c, a)
        assertTrue(g.detectCycle())
    }

    @Test
    fun topologicalSortSimple() {
        val g = DependencyGraph()
        val a = CellAddress("Sheet1", 1, "A")
        val b = CellAddress("Sheet1", 1, "B")
        val c = CellAddress("Sheet1", 1, "C")
        g.addEdge(a, b)
        g.addEdge(b, c)
        assertFalse(g.detectCycle())
        val order = g.topologicalSort()
        assert(order.isNotEmpty())
    }
}
