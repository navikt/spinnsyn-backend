DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'nils.jorgen.mittet@nav.no')
        THEN
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "nils.jorgen.mittet@nav.no";
        END IF;
    END
$$
