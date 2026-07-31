package com.datasandbox.pro.mcp

import com.datasandbox.pro.core.FormulaEngine

/**
 * Lightweight MCP server stub for DataSandbox Pro.
 *
 * This class is a placeholder to outline the tools that will be exposed via MCP.
 * Replace these simple functions with proper MCP annotations / registration when
 * integrating the android-mcp-sdk.
 */
class DataSandboxMcpServer {

    fun calculateFormula(formula: String, parameters: Map<String, Double> = emptyMap()): String {
        // For now delegate to the local FormulaEngine evaluate placeholder
        return FormulaEngine.evaluate(formula, parameters)
    }

    fun queryTable(tableName: String, where: String? = null, limit: Int = 100): String {
        // TODO: Query the local SQLite `.dsb` document and return JSON
        return "{\"error\": \"queryTable not implemented yet\"}"
    }

    fun traceDependencies(tableName: String, cellAddress: String): String {
        // TODO: Use GridEngine/DependencyGraph to return dependency tree
        return "[]"
    }

    fun suggestFormula(tableName: String, columnName: String): String {
        // TODO: Call LocalAIAssistant to suggest a formula based on sample data
        return "NOT_IMPLEMENTED"
    }
}
