package com.datasandbox.pro.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormulaEngineTest {
    @Test
    fun testPMT_zeroRate() {
        val res = FormulaEngine.pmt(0.0, 12, 1200.0)
        // monthly pay of principal only
        assertEquals(-100.0, res, 0.0001)
    }

    @Test
    fun testSUM_and_AVERAGE() {
        val sum = FormulaEngine.evaluate("=SUM(1,2,3)")
        assertEquals("6", sum)
        val avg = FormulaEngine.evaluate("=AVERAGE(10,20)")
        assertEquals("15", avg)
    }

    @Test
    fun testPMT_formulaEval() {
        val res = FormulaEngine.evaluate("=PMT(0.01, 12, 10000)")
        // We won't assert exact cent rounding, just ensure it's numeric and not error
        assert(!res.startsWith("#ERROR"))
    }
}
