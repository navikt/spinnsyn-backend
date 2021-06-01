DELETE
FROM vedtak_v2;

DELETE
FROM utbetaling;

ALTER TABLE vedtak_v2
    ADD CONSTRAINT vedtak_v2_utbetaling_id_unique UNIQUE (utbetaling_id);

ALTER TABLE utbetaling
    ADD CONSTRAINT utbetaling_utbetaling_id_unique UNIQUE (utbetaling_id);
