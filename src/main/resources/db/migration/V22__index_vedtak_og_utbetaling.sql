create index vedtak_utbetaling_id_idx on vedtak_v2 (utbetaling_id);

create index utbetaling_antall_vedtak_idx on utbetaling (antall_vedtak, varslet_med);
