# Database migration foundation

Flyway is present but **disabled by default**. The application keeps the existing
Hibernate `ddl-auto=update` behaviour until an operator baselines an existing
database from its *inspected* schema.

## Why Flyway is opt-in

Read-only inspection of the development database (`salaryai_db`, PostgreSQL
17.11, `public` schema) on 2026-11-28 found only `organizations` and
`platform_admins` and no Flyway history table. `V1__baseline.sql` reconstructs
exactly those two tables from catalogue metadata (not from entity annotations)
and is a no-op when they already exist. `V2` creates the Phase 3 `employees`
table; `V3` creates the Phase 4 attendance tables.

`spring.flyway.baseline-on-migrate=false` and `spring.flyway.enabled=false` are
deliberate: an existing database with data must never be baselined automatically.

## One-time adoption on an existing environment (manual, operator-controlled)

1. Take a verified backup.
2. Confirm the existing schema matches the inspected metadata documented in the
   project report (organization/platform-admin columns, indexes and uniqueness).
3. Run Flyway's `baseline` command once with baseline version `1` against that
   database. This records history without altering existing tables or rows.
4. Start the application with the `migration` profile enabled
   (`spring.profiles.active=local,migration`). Flyway applies `V2` and `V3`;
   Hibernate uses `validate` in that profile.
5. Verify `flyway_schema_history` and schema metadata after the run.

If the inspected schema does **not** match, correct it with a separate,
reviewed migration before baselining. Do not rely on Hibernate update to
reconcile a Flyway-managed schema.

## Automated verification

`AttendanceMigrationTest` verifies migrations in a dedicated schema inside the
isolated `salaryai_test` database. It never targets `salaryai_db`, never creates
a second datasource, and never modifies the test datasource guard infrastructure.
Flyway creates the dedicated schema itself; the test drops only that dedicated
schema afterwards.
