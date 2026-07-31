-- .dsb SQLite schema for DataSandbox Pro (initial draft)
-- Place this file in app/src/main/assets or a `dbschema/` folder for reference.

CREATE TABLE IF NOT EXISTS _metadata (
    key TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS tables (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS columns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_id INTEGER REFERENCES tables(id),
    name TEXT,
    type TEXT,
    formula TEXT,
    is_primary_key INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rows (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_id INTEGER REFERENCES tables(id),
    row_index INTEGER,
    data_json TEXT
);

CREATE TABLE IF NOT EXISTS dashboards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,
    widgets_json TEXT
);

CREATE TABLE IF NOT EXISTS pattern_weights (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pattern_signature TEXT UNIQUE,
    tool_sequence TEXT,
    success_score REAL,
    execution_time_ms INTEGER,
    frequency_used INTEGER DEFAULT 0
);
