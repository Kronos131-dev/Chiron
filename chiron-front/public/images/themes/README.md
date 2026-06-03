# Assets des univers de profil — « Voyage du Héros »

Dépose ici les images. Le code les charge automatiquement ; **tant qu'un fichier est
absent, un fallback CSS s'affiche** (pas d'image cassée, pas de zone vide). Tu peux donc
en ajouter une par une et voir le rendu progresser.

Chemin servi : `/images/themes/<fichier>`. Optimise pour le mobile (WebP/PNG compressés).

## Les 3 univers (par catégorie de palier)

- **novice** (paliers 1-2 — Éphèbe, Argonaute) : « L'Appel de l'Aventure ». Égée nuit→aube, départ, pierre + bronze, bleus profonds, étoiles.
- **athlete** (paliers 3-5 — Hoplite, Myrmidon, Spartiate) : « L'Épreuve du Héros ». Temple aux torches, guerre, marbre, bronze/fer, bannières oxblood→améthyste.
- **legend** (paliers 6-8 — Héros, Dieu, Olympien) : « L'Apothéose ». Olympe, nuées dorées, rayons divins, or + ivoire, constellations.

Cohérence visuelle : clair-obscur, lumière chaude rasante, **fond sombre** (l'app est noire).

## Fichiers attendus

| Fichier | Type | Dimensions | Transparence |
|---|---|---|---|
| `bg-novice.webp` | fond de scène | 1280×1920 (9:16) | non |
| `bg-athlete.webp` | fond de scène | 1280×1920 | non |
| `bg-legend.webp` | fond de scène | 1280×1920 | non |
| `frame-novice.png` | cadre 9-slice | 900×1200, marge déco ~90 px | OUI (centre vide) |
| `frame-athlete.png` | cadre 9-slice | 900×1200, ~90 px | OUI |
| `frame-legend.png` | cadre 9-slice | 900×1200, ~90 px | OUI |
| `column-novice.png` | colonne | 320×1200 | OUI |
| `column-athlete.png` | colonne | 320×1200 | OUI |
| `column-legend.png` | colonne | 320×1200 | OUI |
| `pediment-novice.png` | fronton | 1000×360 | OUI |
| `pediment-athlete.png` | fronton | 1000×360 | OUI |
| `pediment-legend.png` | fronton | 1000×360 | OUI |

> Le **cadre** (`frame-*.png`) est découpé en « 9-slice » : la bordure décorative doit
> faire ~90 px d'épaisseur tout autour, et **le centre doit être totalement transparent**
> (c'est là que vient le contenu de la carte). Le code utilise `border-image ... 90`.

## Bibliothèques open-access

- **The Met Open Access (CC0)** — vases & sculptures grecques : metmuseum.org (filtre Open Access)
- **Wikimedia Commons (PD-art)** — poteries, temples, statues
- **rawpixel.com (CC0/PD)** — ornements, frises méandre, cadres dorés (« greek ornament », « meander border », « ornate gold frame »)
- **SVG Repo** — SVG colonne, laurier, amphore, bouclier, navire, clé grecque
- **Freepik / Vecteezy / Flaticon** (attribution) — « greek column png transparent », « greek pediment png », « gold ornament frame png »
- **Poly Haven (CC0)** & **ambientCG (CC0)** — textures marbre / or / pierre, ciels
- **Unsplash / Pexels** (libre) — marbre, ciel étoilé, nuages, temples grecs

## Prompts IA

Style commun (fonds) :
`ancient Greek mythology, cinematic matte painting, dramatic chiaroscuro, deep dark background, warm golden rim light, highly detailed, atmospheric, volumetric light, artstation quality, vertical composition --ar 9:16`

- **bg-novice** : `…a lone young Greek adventurer on a cliff at dawn over the Aegean sea, a distant ship (the Argo) on the horizon, faint constellations fading, teal and bronze palette, hopeful, mist…`
- **bg-athlete** : `…interior of a torch-lit Greek temple at night, towering marble Doric columns, drifting smoke, deep crimson banners, bronze and stone, heroic, embers…`
- **bg-legend** : `…summit of Mount Olympus above golden storm clouds, radiant god-rays from above, distant stars and a hero constellation, gold and ivory, divine, ethereal…`

Éléments à détourer (ajouter pour faciliter la découpe sous Google) :
`centered, symmetrical, orthographic front view, isolated on flat neutral grey background, even lighting, no text, no ground shadow, PNG cutout ready`

- **frame-{cat}** : `ornate ancient Greek rectangular picture frame, hollow empty transparent center, carved {bronze patina | bronze and iron with laurel and spears | gilded gold with acanthus and gemstones}, Greek key meander border, museum quality`
- **column-{cat}** : `single tall ancient Greek {weathered stone Doric | marble Doric with a crimson banner | gilded glowing Corinthian} column, full height, capital and base`
- **pediment-{cat}** : `ancient Greek triangular temple pediment (tympanum), low-relief sculpture of {a single star and a ship | crossed shields and spears | the eagle of Zeus and a sun}, {bronze | stone and bronze | gold}`

Après génération sur fond plat → **supprime l'arrière-plan** (Google / outil de détourage)
et exporte en PNG transparent aux dimensions ci-dessus.
