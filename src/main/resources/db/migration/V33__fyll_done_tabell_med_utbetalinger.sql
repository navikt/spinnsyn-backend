INSERT INTO done_vedtak(id, fnr, type)
SELECT varslet_med, fnr, 'UTBETALING'
FROM utbetaling
WHERE varslet_med IS NOT NULL
  AND lest IS NULL;
