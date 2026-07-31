# DataSandbox Pro

Excel-like offline spreadsheet for Android. SQLite-backed `.dsb` document
format, dependency-graph recalculation, and an Excel-compatible formula
engine (SUM, AVERAGE, IF, VLOOKUP-style lookups, PMT/FV/PV/NPER/NPV, text
functions, etc.).

## Modules implemented in this scaffold

- `model/` — Cell, CellAddress, CellValue, table/column definitions
- `engine/` — `DependencyGraph` (Kahn's algorithm + cycle detection),
  `FormulaEngine` (lexer, recursive-descent parser, function library)
- `data/` — `DsbDatabase` (SQLite schema: tables, columns, rows, dashboards,
  pattern_weights), `SheetRepository` (CRUD + recalculation)
- `viewmodel/` + `ui/` — Jetpack Compose grid with a formula bar

Not yet implemented (see blueprint): MCP server exposure, dashboard
builder widgets, local AI formula assistant, CSV/XLSX import-export,
WebDAV sync, template library. The architecture (SQLite `.dsb` format,
dependency graph, pluggable function library) is built to support all of
these as incremental additions.

## Build locally

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Build via GitHub Actions

Push to `main` (or open a PR) — `.github/workflows/android-build.yml` runs
lint, builds a debug APK and an unsigned release APK, and uploads both as
workflow artifacts. Trigger manually via the "Run workflow" button
(`workflow_dispatch`) if needed.

## Requirements

- JDK 17
- Android SDK (compileSdk 34, minSdk 24)
