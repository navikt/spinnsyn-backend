CREATE TABLE done_vedtak
(
    id         VARCHAR(36) NOT NULL,
    fnr        VARCHAR(11) NOT NULL,
    type       VARCHAR(11) NOT NULL,
    done_sendt TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id, fnr)
);
