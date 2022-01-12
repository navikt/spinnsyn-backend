INSERT INTO done_vedtak(id, fnr, type)
SELECT id, fnr, 'VEDTAK'
FROM vedtak
WHERE lest IS NULL;
