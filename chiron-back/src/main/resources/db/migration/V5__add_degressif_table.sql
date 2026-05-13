CREATE TABLE degressif (
    id SERIAL PRIMARY KEY,
    poids DOUBLE PRECISION NOT NULL,
    nombre_reps INTEGER NOT NULL,
    serie_id BIGINT,
    CONSTRAINT fk_degressif_serie FOREIGN KEY (serie_id) REFERENCES serie(id) ON DELETE CASCADE
);

