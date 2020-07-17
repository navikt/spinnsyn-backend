CREATE TABLE vedtak
(
    id VARCHAR(36) PRIMARY KEY,
    fnr VARCHAR(11) NOT NULL,
    opprettet TIMESTAMP NOT NULL,
    vedtak JSONB NOT NULL
);

