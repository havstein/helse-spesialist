CREATE TABLE automatisering
(
    vedtaksperiode_ref INT  NOT NULL REFERENCES vedtak (id) ON DELETE CASCADE,
    hendelse_ref       uuid NOT NULL REFERENCES hendelse (id),
    automatisert       BOOLEAN,
    PRIMARY KEY (vedtaksperiode_ref, hendelse_ref)
);
