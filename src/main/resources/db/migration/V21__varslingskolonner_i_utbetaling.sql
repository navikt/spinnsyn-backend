ALTER TABLE utbetaling
    ADD COLUMN lest TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN brukernotifikasjon_sendt TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN brukernotifikasjon_utelatt TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN varslet_med VARCHAR(36);