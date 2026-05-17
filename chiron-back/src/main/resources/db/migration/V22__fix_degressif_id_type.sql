-- V5 a déclaré degressif.id en SERIAL (INTEGER), mais l'entité JPA Degressif utilise un Long.
-- Sur une base fraîche, Hibernate (ddl-auto=validate) refuse de démarrer.
-- On aligne le type sur BIGINT et on bascule la séquence sur BIGINT pour matcher BIGSERIAL.

ALTER TABLE degressif ALTER COLUMN id TYPE BIGINT;
ALTER SEQUENCE IF EXISTS degressif_id_seq AS BIGINT;
