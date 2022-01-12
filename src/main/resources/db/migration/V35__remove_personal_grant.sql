DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'nils.jorgen.mittet@nav.no')
        THEN
            REVOKE SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM "nils.jorgen.mittet@nav.no";
        END IF;
    END
$$