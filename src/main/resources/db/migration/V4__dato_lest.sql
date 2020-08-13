ALTER TABLE vedtak
    ALTER COLUMN lest DROP NOT NULL;
ALTER TABLE vedtak
    ALTER COLUMN lest DROP DEFAULT;
ALTER TABLE vedtak
    ALTER COLUMN lest
        SET DATA TYPE timestamp
        USING null;
ALTER TABLE vedtak
    ALTER COLUMN lest
        SET DEFAULT null;
