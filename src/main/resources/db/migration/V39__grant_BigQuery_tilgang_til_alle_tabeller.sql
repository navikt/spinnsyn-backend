DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'bigquery-dataprodukt')
        THEN
            GRANT SELECT ON done_vedtak TO "bigquery-dataprodukt";
            GRANT SELECT ON organisasjon TO "bigquery-dataprodukt";
            GRANT SELECT ON vedtak_v2 TO "bigquery-dataprodukt";
        END IF;
    END
$$;
