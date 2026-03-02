CREATE TABLE IF NOT EXISTS db_connection (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  db_type TEXT NOT NULL,
  host TEXT NOT NULL,
  port INTEGER NOT NULL,
  username TEXT NOT NULL,
  password TEXT NOT NULL,
  db_name TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS backup_policy (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  connection_id INTEGER NOT NULL,
  database_name TEXT NOT NULL,
  tables_csv TEXT,
  mode TEXT NOT NULL,
  interval_minutes INTEGER NOT NULL,
  backup_path TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  last_run_at TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY (connection_id) REFERENCES db_connection(id)
);

CREATE TABLE IF NOT EXISTS backup_job (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  policy_id INTEGER NOT NULL,
  status TEXT NOT NULL,
  message TEXT,
  file_path TEXT,
  started_at TEXT NOT NULL,
  ended_at TEXT,
  FOREIGN KEY (policy_id) REFERENCES backup_policy(id)
);

CREATE TABLE IF NOT EXISTS app_notice (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  level TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at TEXT NOT NULL,
  handled INTEGER NOT NULL DEFAULT 0
);