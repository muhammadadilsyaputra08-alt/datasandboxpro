package com.datasandbox.pro.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecalcEngineTest {
    @Test
    fun testSimpleRecalc() {
        val a1 = Cell(CellAddress("Sheet1", 1, "A"), CellValue.Number(10.0), null)
        val a2 = Cell(CellAddress("Sheet1", 2, "A"), CellValue.Empty, "=SUM(A1,5)")
        val cells = mapOf(a1.address to a1, a2.address to a2)
        val engine = RecalcEngine()
        val out = engine.recalc(cells)
        val a2out = out[a2.address]
        when (val v = a2out?.value) {
            is CellValue.Number -> assertEquals(15.0, v.value, 0.0001)
            else -> throw AssertionError("expected numeric result")
        }
    }
}
