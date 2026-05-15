-- Pre-seeds usage_count with realistic gym popularity scores.
-- Tier 1 (50000): The universal "big" lifts every gym-goer knows.
-- Tier 2 (30000): Very common compound / staple exercises.
-- Tier 3 (15000): Popular accessory and isolation work.
-- Tier 4 (7000):  Moderately known exercises.
-- Tier 5 (2000):  Less common but established movements.
-- Exercises not listed keep usage_count = 0 (chains, bands, exotic variants, etc.)

-- ── Tier 1: absolute staples ────────────────────────────────────────────────
UPDATE exercice_definition SET usage_count = 50000 WHERE LOWER(nom_fr) IN (
    'squat barre',
    'soulevé de terre barre',
    'développé couché barre prise moyenne',
    'tractions',
    'développé militaire barre',
    'pompes'
);

-- ── Tier 2: very common ──────────────────────────────────────────────────────
UPDATE exercice_definition SET usage_count = 30000 WHERE LOWER(nom_fr) IN (
    -- Poitrine
    'développé couché haltères',
    'développé couché prise serrée barre',
    'développé incliné haltères',
    'développé incliné barre prise moyenne',
    'développé décliné barre',
    'écarté incliné haltères',
    'dips pectoraux',
    'dips aux barres parallèles',
    -- Épaules
    'développé militaire barre assis',
    'développé militaire debout',
    'développé épaules haltères',
    'développé épaules haltères assis',
    'élévation latérale',
    'élévation frontale haltères',
    -- Dos
    'tirage poulie prise large',
    'tirage poitrine prise serrée',
    'rowing barre penché',
    'rowing haltère unilatéral',
    'rowing poulie basse assis',
    'tractions lestées',
    'traction prise supination',
    -- Jambes
    'squat haltères',
    'squat poids du corps',
    'squat avant barre',
    'squat complet barre',
    'leg press',
    'fente barre',
    'fentes haltères',
    'soulevé de terre roumain',
    'soulevé de terre jambes tendues barre',
    'soulevé de terre sumo',
    -- Biceps
    'curl barre',
    'curl biceps haltères',
    'curl biceps haltères alterné',
    -- Triceps
    'dips triceps',
    'dips sur banc',
    -- Abdos
    'crunchs',
    'gainage'
);

-- ── Tier 3: popular accessories ──────────────────────────────────────────────
UPDATE exercice_definition SET usage_count = 15000 WHERE LOWER(nom_fr) IN (
    -- Poitrine
    'développé couché smith',
    'développé incliné smith',
    'développé incliné haltères paumes face à face',
    'développé couché haltères prise neutre',
    'développé couché machine',
    'écarté incliné poulie',
    -- Épaules
    'arnold press haltères',
    'élévation latérale unilatérale',
    'élévation frontale deux haltères',
    'élévation arrière haltères penché tête sur banc',
    'élévation arrière deltoïde assis penché',
    'rowing vertical barre',
    'rowing vertical haltères debout',
    -- Dos
    'tirage nuque prise large',
    'tirage poulie prise sous-main',
    'rowing deux haltères penché',
    'rowing machine levier',
    'rowing vertical smith',
    'rowing inversé',
    'traction assistée par élastique',
    -- Jambes
    'squat avec élastiques',
    'squat smith',
    'hack squat barre',
    'fente arrière haltères',
    'curl ischio allongé machine',
    'curl ischio assis machine',
    'extension jambes machine',
    'relevé de mollets debout',
    -- Biceps
    'curl barre ez',
    'curl haltères assis',
    'curl incliné haltères',
    'curl concentration',
    'curl biceps machine',
    'curl biceps poulie debout',
    -- Triceps
    'extension triceps corde overhead',
    'extension triceps barre ez décliné',
    'développé triceps allongé',
    'développé couché prise serrée smith',
    -- Abdos
    'crunch inversé',
    'relevé de jambes suspendu',
    'gainage'
);

-- ── Tier 4: moderately known ─────────────────────────────────────────────────
UPDATE exercice_definition SET usage_count = 7000 WHERE LOWER(nom_fr) IN (
    -- Poitrine
    'développé couché poulie debout',
    'développé couché poulie',
    'développé décliné haltères',
    'développé décliné smith',
    'développé couché machine levier',
    'écarté inverse machine',
    -- Épaules
    'développé épaules machine levier',
    'élévation latérale assis poulie',
    'élévation latérale avec élastiques',
    'élévation frontale poulie',
    'rowing vertical poulie',
    -- Dos
    'tirage poulie barre en v',
    'rowing barre assis sur banc',
    'rowing iso machine levier',
    'rowing suspendu',
    'rowing inversé avec sangles',
    'pull-over bras tendus haltère',
    -- Jambes
    'fente marchée barre',
    'squat fendu haltères',
    'squat pistol kettlebell',
    'presse mollets',
    'soulevé de terre barre hexagonale',
    -- Biceps
    'curl barre inversé',
    'curl barre prise serrée debout',
    'curl zottman',
    'curl biceps interne haltères assis',
    'alternate hammer curl',
    -- Triceps
    'développé triceps assis',
    'dips aux anneaux',
    -- Abdos
    'crunch poulie',
    'crunch inversé suspendu',
    'relevé de buste avec développé'
);

-- ── Tier 5: less common but established ──────────────────────────────────────
UPDATE exercice_definition SET usage_count = 2000 WHERE LOWER(nom_fr) IN (
    'développé nuque',
    'développé penché',
    'développé sol barre',
    'développé sol haltères',
    'développé guillotine barre',
    'développé couché bande inversée',
    'développé couché powerlifting',
    'développé épaules poulie',
    'développé assis kettlebell',
    'épaulé-développé',
    'rowing kettlebell unilatéral',
    'rowing renegade alterné',
    'rowing t-bar avec poignée',
    'soulevé de terre en déficit',
    'soulevé de terre arraché',
    'squat zercher',
    'squat jefferson',
    'squat olympique',
    'squat avant prise olympique',
    'curl barre ez prise serrée',
    'curl incliné haltères alterné'
);
