UPDATE UTBETALING
    SET motatt_publisert = CURRENT_TIMESTAMP
    WHERE motatt_publisert IS NULL;
