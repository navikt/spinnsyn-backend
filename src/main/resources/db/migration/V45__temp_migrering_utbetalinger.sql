CREATE TABLE IF NOT EXISTS temp_utbetalinger_migrering
(
    id            SERIAL PRIMARY KEY,
    utbetaling_id TEXT NOT NULL UNIQUE,
    status        TEXT NOT NULL DEFAULT 'IKKE_MIGRERT'
);

CREATE INDEX IF NOT EXISTS idx_temp_utbetalinger_migrering_status ON temp_utbetalinger_migrering (status);