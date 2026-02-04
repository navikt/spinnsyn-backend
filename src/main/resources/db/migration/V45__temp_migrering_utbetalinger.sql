CREATE TABLE IF NOT EXISTS temp_utbetalinger_migrering
(
    id            SERIAL PRIMARY KEY,
    utbetaling_id TEXT NOT NULL UNIQUE,
    status        TEXT NOT NULL      DEFAULT 'IKKE_MIGRERT'
);

CREATE INDEX IF NOT EXISTS idx_temp_utbetalinger_migrering_status ON temp_utbetalinger_migrering (status);

INSERT INTO temp_utbetalinger_migrering (utbetaling_id, status)
SELECT utbetaling_id, 'IKKE_MIGRERT'
FROM utbetaling
WHERE opprettet < '2026-01-14T12:22:29.756007Z'
ON CONFLICT (utbetaling_id) DO NOTHING;