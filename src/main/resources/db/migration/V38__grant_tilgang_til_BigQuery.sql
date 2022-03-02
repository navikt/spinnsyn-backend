DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'bigquery-dataprodukt')
        THEN
            GRANT SELECT ON utbetaling TO "bigquery-dataprodukt";
            GRANT SELECT ON annullering TO "bigquery-dataprodukt";
        END IF;
    END
$$;
