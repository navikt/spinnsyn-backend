CREATE TABLE vedtak
(
    id CHAR(64) PRIMARY KEY,
    fnr CHAR(11) NOT NULL,
    opprettet TIMESTAMP NOT NULL,
    vedtak JSONB NOT NULL
);

