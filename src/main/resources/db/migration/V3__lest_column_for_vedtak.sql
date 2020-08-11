ALTER TABLE vedtak
    ADD COLUMN lest boolean
        not null
        default false;
