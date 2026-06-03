# Assets des univers de profil — « Voyage du Héros »

Les images d'ici sont chargées automatiquement par le profil (`profile.css`) et les cartes
Agora (`agora.css`). Tant qu'un fichier est absent, un **fallback CSS** s'affiche (pas
d'image cassée, pas de zone vide). Chemin servi : `/images/themes/<fichier>`.

## Les 3 univers (par catégorie de palier)

- **novice** (paliers 1-2 — Éphèbe, Argonaute) : « L'Appel de l'Aventure ». Aventurier sur la falaise à l'aube, nef Argo à l'horizon, étoiles, bleus profonds + bronze.
- **athlete** (paliers 3-5 — Hoplite, Myrmidon, Spartiate) : « L'Épreuve du Héros ». Temple aux torches, fumée, bannière pourpre, marbre, bronze/fer.
- **legend** (paliers 6-8 — Héros, Dieu, Olympien) : « L'Apothéose ». Olympe, nuées dorées, rayons divins, trône, constellation, or + ivoire.

Cohérence visuelle : clair-obscur, lumière chaude rasante, **fond sombre** (l'app est noire).

## Fichiers en place

| Fichier | Rôle | Dimensions | Notes |
|---|---|---|---|
| `bg-novice.webp` | fond de scène plein écran | 1080×1935 | converti/optimisé (~33 Ko) |
| `bg-athlete.webp` | fond de scène plein écran | 1080×1935 | ~77 Ko |
| `bg-legend.webp` | fond de scène plein écran | 1080×1935 | ~75 Ko |
| `frame-novice.png` | cadre `border-image` | 892×1144 | anneau orné, méandre patiné vert |
| `frame-athlete.png` | cadre `border-image` | 892×1144 | bronze/fer + laurier |
| `frame-legend.png` | cadre `border-image` | 892×1144 | or sculpté + gemmes |

### Fonds (`bg-*.webp`)
- Générés en `.png` 1536×2752 puis **convertis** en WebP optimisé (mobile-first, < 300 Ko) :
  `convert bg-{cat}.png -resize 1080x -strip -quality 80 -define webp:method=6 bg-{cat}.webp`.
- Si tu en régénères un, refais cette conversion (ne pas commiter le `.png` 6 Mo).

### Cadres (`frame-*.png`)
- Utilisés en `border-image: url(...) 16% 14% / 1 / 0 round` (pas de `fill` → le centre
  brumeux est ignoré, jamais peint sur le contenu). Le slice est en **pourcentage** :
  reste valide même si tu régénères un cadre à d'autres dimensions.
- Idéal : anneau décoratif net sur ~14-17 % de chaque bord, centre « fenêtre » (peu importe
  qu'il soit transparent ou brumeux, il est découpé). Calage fin du slice dans `profile.css`.

## Colonnes & frontons — RETIRÉS

Les colonnes latérales et le fronton ont été **retirés du design** : les fonds de scène
contiennent déjà des colonnes (temple, Olympe) et le cadre orné encadre la carte. Inutile
de fournir `column-*.png` / `pediment-*.png`.

## Bibliothèques open-access (si besoin futur)

- **The Met Open Access (CC0)**, **Wikimedia Commons (PD-art)** — vases, sculptures, temples
- **rawpixel.com (CC0/PD)** — ornements, frises méandre, cadres dorés
- **Poly Haven / ambientCG (CC0)** — textures marbre / or / pierre, ciels
- **Unsplash / Pexels** — marbre, ciel étoilé, nuages, temples grecs

## Prompts IA de référence

Style commun (fonds) :
`ancient Greek mythology, cinematic matte painting, dramatic chiaroscuro, deep dark background, warm golden rim light, highly detailed, atmospheric, volumetric light, vertical composition --ar 9:16`

- **bg-novice** : `…a lone young Greek adventurer on a cliff at dawn over the Aegean sea, a distant ship (the Argo) on the horizon, faint constellations, teal and bronze palette, hopeful, mist…`
- **bg-athlete** : `…interior of a torch-lit Greek temple at night, towering marble Doric columns, drifting smoke, deep crimson banners, bronze and stone, heroic, embers…`
- **bg-legend** : `…summit of Mount Olympus above golden storm clouds, radiant god-rays, a throne, distant stars and a hero constellation, gold and ivory, divine…`

Cadres (fond plat pour découpe) : `ornate ancient Greek rectangular picture frame, hollow empty center, carved {bronze patina + Greek key meander | bronze and iron with laurel | gilded gold with acanthus and gemstones}, isolated on flat neutral background, museum quality`
