---
name: database-schema-alignment
description: Use when database tables and columns need to be aligned with application entity definitions (JPA/Hibernate/ORM). Use when the user says "对齐数据库", "修复数据库表", "同步数据库", "migrate database", "sync schema", or when entity field changes are detected. Covers a five-phase Extract→Query→Diff→Execute→Verify alignment workflow with idempotent DDL generation.
---

# Database Schema Alignment

## Core Principle

**The database schema must exactly mirror the application's entity definitions.** When an entity class changes (field added, type modified, constraint adjusted), the corresponding database table must be updated. This skill defines the methodology for doing so safely, idempotently, and verifiably.

## Five-Phase Alignment Workflow

```
Extract ──→ Query ──→ Diff ──→ Execute ──→ Verify
(entity)    (DB)      (gap)     (DDL)       (confirm)
```

### Phase 1: Extract — Derive Expected Schema from Entities

Read application entity/model classes and extract, for each persistent field:

| Attribute | Source |
|-----------|--------|
| Column name | Field name (or `@Column(name="...")` override) |
| SQL type | Java type → DB type mapping (see Type Mapping table below) |
| Nullable | `@Column(nullable=false)` → NOT NULL, otherwise nullable unless `@ManyToOne`/`@OneToOne` with `optional=false` |
| Max length | `@Column(length=N)` → VARCHAR(N); default varies by framework |
| Default value | `@ColumnDefault("...")` or `@Builder.Default` |
| Primary key | `@Id` → PRIMARY KEY |
| Foreign key | `@ManyToOne`/`@OneToOne` join column → REFERENCES |

### Phase 2: Query — Retrieve Actual Database Schema

Query the database's system catalog to get the real state:

**PostgreSQL:**
```sql
SELECT column_name, data_type, character_maximum_length,
       is_nullable, column_default, udt_name
FROM information_schema.columns
WHERE table_name = ?
ORDER BY ordinal_position;
```

**MySQL:**
```sql
SELECT column_name, data_type, character_maximum_length,
       is_nullable, column_default
FROM information_schema.columns
WHERE table_name = ? AND table_schema = ?;
```

Also list all tables:
```sql
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';  -- PG
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE(); -- MySQL
```

### Phase 3: Diff — Compare and Categorize Discrepancies

Compare every expected column against the actual schema. Categorize each difference:

| Category | Detection | Severity |
|----------|-----------|----------|
| **Missing table** | Entity has no corresponding DB table | Critical |
| **Missing column** | Entity field has no matching column | High |
| **Type mismatch** | `data_type` differs (e.g., VARCHAR vs TEXT, INTEGER vs BIGINT) | High |
| **Length mismatch** | `character_maximum_length` differs | Medium |
| **Nullable mismatch** | `is_nullable` differs from expected (column is NULL but should be NOT NULL) | High |
| **Default mismatch** | `column_default` differs | Low |
| **Extra column** | DB column has no matching entity field | Warning (don't auto-drop) |

### Phase 4: Execute — Generate and Run Idempotent DDL

#### 4.1 Execution Order Matters

Always execute in dependency order:
1. New tables (with their FK references)
2. New columns (nullable first, then populate, then SET NOT NULL)
3. Type changes (with USING clause)
4. Constraint changes (NOT NULL, DEFAULT)
5. Column renames (add new → migrate data → drop old)
6. Drop operations (only after user confirmation)

#### 4.2 Statement Templates

**Add column:**
```sql
ALTER TABLE <table> ADD COLUMN IF NOT EXISTS <column> <type> <constraints>;
```

**Change type:**
```sql
ALTER TABLE <table> ALTER COLUMN <column> TYPE <new_type> USING (<column>::<new_type>);
```

**Set NOT NULL (after data check):**
```sql
-- First: ensure no nulls
UPDATE <table> SET <column> = <default> WHERE <column> IS NULL;
-- Then: add constraint
ALTER TABLE <table> ALTER COLUMN <column> SET NOT NULL;
```

**Set DEFAULT:**
```sql
ALTER TABLE <table> ALTER COLUMN <column> SET DEFAULT <value>;
```

**Rename column (data-preserving):**
```sql
ALTER TABLE <table> ADD COLUMN <new_column> <type>;
UPDATE <table> SET <new_column> = <old_column>;
ALTER TABLE <table> ALTER COLUMN <new_column> SET NOT NULL;
-- Only after verifying data integrity:
ALTER TABLE <table> DROP COLUMN <old_column>;
```

#### 4.3 Idempotency Rules

Every statement must be safe to re-run:
- `ADD COLUMN IF NOT EXISTS` (PostgreSQL 9.6+)
- `DO $$ BEGIN ... EXCEPTION ... END $$` for older PostgreSQL
- Check `information_schema` before applying: `IF EXISTS (SELECT 1 FROM information_schema.columns WHERE ...)`
- Idempotency source of truth: **information_schema**, not an application-side migration table

#### 4.4 What NOT to Execute Automatically

| Operation | Rule |
|-----------|------|
| `DROP TABLE` | Require explicit user confirmation |
| `DROP COLUMN` | Require explicit user confirmation (data loss) |
| `DROP CONSTRAINT` | Require explicit user confirmation |
| `TRUNCATE` | Never execute without user request |
| Type change that loses data (e.g., VARCHAR(50)→VARCHAR(10)) | Warn and require confirmation |

### Phase 5: Verify — Confirm Alignment

After execution, re-run the Phase 2 query and re-run the Phase 3 diff. The result must show **zero remaining discrepancies**.

Report format:
```
Schema Alignment Report
=======================
Tables checked: N
Applied: X (list each change)
Skipped (already aligned): Y
Errors: Z (list each with reason)
Final status: ✅ Fully aligned / ⚠️ N issues remaining
```

## Java → Database Type Mapping

| Java Type | PostgreSQL | MySQL | Notes |
|-----------|-----------|-------|-------|
| `String` | `VARCHAR(n)` | `VARCHAR(n)` | Length from `@Column(length=N)`, default 255 |
| `String` (no length, or `@Lob`) | `TEXT` | `TEXT` / `LONGTEXT` | |
| `Integer` / `int` | `INTEGER` | `INT` | |
| `Long` / `long` | `BIGINT` | `BIGINT` | |
| `Short` / `short` | `SMALLINT` | `SMALLINT` | |
| `Double` / `double` | `DOUBLE PRECISION` | `DOUBLE` | |
| `Float` / `float` | `REAL` | `FLOAT` | |
| `BigDecimal` | `DECIMAL(p,s)` | `DECIMAL(p,s)` | Precision/scale from `@Column(precision,scale)` |
| `Boolean` / `boolean` | `BOOLEAN` | `BOOLEAN` / `TINYINT(1)` | |
| `java.util.Date` / `java.sql.Date` | `DATE` | `DATE` | `@Temporal(TemporalType.DATE)` |
| `java.util.Date` / `java.sql.Timestamp` | `TIMESTAMP` | `TIMESTAMP` / `DATETIME` | `@Temporal(TemporalType.TIMESTAMP)` |
| `LocalDateTime` | `TIMESTAMP` | `DATETIME` | |
| `LocalDate` | `DATE` | `DATE` | |
| `LocalTime` | `TIME` | `TIME` | |
| `UUID` | `UUID` | `CHAR(36)` / `BINARY(16)` | |
| `Enum` (`@Enumerated(STRING)`) | `VARCHAR(n)` | `VARCHAR(n)` | Length = longest enum constant name |
| `Enum` (`@Enumerated(ORDINAL)`) | `INTEGER` | `INT` | Discouraged — use STRING |
| `byte[]` / `Byte[]` (`@Lob`) | `BYTEA` | `LONGBLOB` | |

## JPA Annotation → DDL Constraint Mapping

| Annotation | DDL Effect |
|-----------|------------|
| `@Id` | `PRIMARY KEY` |
| `@GeneratedValue` | `DEFAULT` (sequence/identity/uuid depending on strategy) |
| `@Column(nullable=false)` | `NOT NULL` |
| `@Column(length=50)` | `VARCHAR(50)` |
| `@Column(unique=true)` | `UNIQUE` constraint |
| `@Column(columnDefinition="TEXT")` | Override auto-mapping, use specified type |
| `@ColumnDefault("value")` | `DEFAULT value` |
| `@Enumerated(EnumType.STRING)` | `VARCHAR` (not integer) |
| `@ManyToOne` / `@OneToOne` | FK column + implicit NOT NULL if `optional=false` |
| `@ManyToOne(optional=true)` | FK column, nullable |
| `@JoinColumn(name="x_id")` | FK column name + `REFERENCES x(id)` |
| `@JoinColumn(insertable=false, updatable=false)` | Read-only FK — column exists but no constraint |
| `@Builder.Default` | Application-level default; should match DB `DEFAULT` |

## Discrepancy Handling Triage

### Automatic (safe, no data loss)
- `ADD COLUMN IF NOT EXISTS` with default value
- `ALTER COLUMN SET DEFAULT`
- `ALTER COLUMN TYPE VARCHAR(N)` → `TEXT` (wider, no data loss)
- `ALTER COLUMN SET NOT NULL` after verifying no nulls

### Automatic with Data Migration
- `ALTER COLUMN TYPE` with `USING` clause (e.g., `VARCHAR→DECIMAL` with `NULLIF`)
- Column rename (add new + copy data + drop old)
- Setting NOT NULL where nulls exist (must provide default first)

### Requires User Confirmation
- `DROP COLUMN` (data destruction)
- `DROP TABLE` (data destruction)
- `ALTER COLUMN TYPE` where data would be truncated (e.g., `TEXT→VARCHAR(10)`)
- Removing or changing FK constraints
- Any change that would cause downtime on a large table (>1M rows — `ALTER TABLE` takes an `ACCESS EXCLUSIVE` lock)

## Safety Principles

1. **Diagnose before operating** — never execute DDL without first querying `information_schema`
2. **One statement at a time** — compound psql scripts with comments can fail on encoding; execute statements individually
3. **Catch and report errors** — a failed DDL statement should not prevent remaining statements from running; collect errors and report them together
4. **Prefer application-side migration** — if the database CLI is unavailable or blocked, generate a migration class (e.g., Spring Boot `CommandLineRunner` + `JdbcTemplate`) that applies the same DDL on application startup
5. **Backup on production** — before any DDL on a production database, verify a recent backup exists or take one
6. **Transaction awareness** — PostgreSQL supports transactional DDL; wrap multi-statement migrations in `BEGIN...COMMIT`. MySQL does NOT support transactional DDL for most statements — test and roll back manually if needed.

## Common Pitfalls

| Pitfall | Prevention |
|---------|-----------|
| Schema.sql and Entity out of sync | Update BOTH when changing entity fields |
| Mismatched default values | `@Builder.Default` in Java must match `DEFAULT` in DB |
| `@Column(length)` ignored by some JPA implementations | Verify via `information_schema`; don't trust JPA's auto-DDL |
| `@Enumerated(ORDINAL)` silently breaks when enum order changes | Always use `EnumType.STRING` |
| `UPDATE` without WHERE corrupting data | Always verify affected row count before committing |
| psql inline password blocked by security tools | Use environment variable (`PGPASSWORD`) or application-side `JdbcTemplate` |
| Idempotency check on wrong schema | Always qualify with `table_schema`; don't assume `public` |
