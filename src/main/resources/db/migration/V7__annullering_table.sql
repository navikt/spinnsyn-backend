CREATE TABLE annullering
(
    id VARCHAR(36) PRIMARY KEY,
    fnr VARCHAR(11) NOT NULL,
    annullering JSONB NOT NULL,
    opprettet TIMESTAMP WITH TIME ZONE NOT NULL
);

create index annullering_fnr_idx on annullering (fnr);
