# BLUEPRINT: DataSandbox Pro

This repo has received initial scaffolding aligned with the project blueprint shared in the issue.

What I added (initial scaffolding)

- app/src/main/java/com/datasandbox/pro/core/FormulaEngine.kt
  - Placeholder FormulaEngine with a PMT implementation and evaluate() stub.

- app/src/main/java/com/datasandbox/pro/core/GridEngine.kt
  - Lightweight Cell/CellAddress types and a DependencyGraph with topologicalSort/detectCycle.

- app/src/main/java/com/datasandbox/pro/mcp/DataSandboxMcpServer.kt
  - MCP server stub exposing calculateFormula/queryTable/traceDependencies/suggestFormula (placeholders).

- app/src/main/java/com/datasandbox/pro/ai/LocalAIAssistant.kt
  - Tiny local AI assistant stub to suggest formulas from sample data.

- app/src/main/assets/dsb_schema.sql
  - Draft SQLite schema for the portable `.dsb` document format.

- templates/sample_template.json
  - Sample template (sales) to seed the application later.

Why these changes

They provide a minimal, safe code surface to start implementing the blueprint features:
- Formula evaluation plumbing (replace evaluate() with parser + evaluator)
- Dependency tracking for recalculation
- MCP tool endpoints to expose functionality to AI clients
- Local AI assistant integration point
- Portable storage schema reference

Next recommended steps (pick one or more):
1. Implement a formula parser (ANTLR grammar) and wire it into FormulaEngine.evaluate().
2. Implement storage layer: create a SQLite-backed Document class that manages tables/rows/columns.
3. Wire DataGrid UI to GridEngine to compute dependencies & recalc on edits.
4. Add unit tests for PMT and DependencyGraph.
5. Integrate android-mcp-sdk to register MCP tools in DataSandboxMcpServer.

If you want, I can start on any of the items above. Which one should I implement next? Or should I continue and implement multiple items in the next commit?
