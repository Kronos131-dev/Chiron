  ⎿  Current Plan
     /home/takima/.claude/plans/noctua-ne-recoit-plus-wiggly-bee.md

     Page Course — tracker GPS avec coaching vocal écran éteint

     Context

     Chiron sait enregistrer une séance de musculation et un WOD, mais rien pour un coureur. L'objectif :
     une page Course, atteinte depuis un exercice de la bibliothèque exactement comme CINDY, qui affiche
     allure, distance et daie nouveauté est le son
     — annonces vocales
     quand on s'écarte d'ubouton du casque, le tout
     écran éteint.

     Contrainte assumée :  Capacitor existe(chiron-front/android
     appId com.kronos.chiron) mais il est nu — zéro plugin, manifeste à une     seule permission INTE
     MainActivity vide, aucun build en CI. Il n'est jamais allé au bout. On reste donc sur la PWA
     installée.                                                                 
     Le pari technique, et il faut le nommer : Chrome Android gèle une page     cachée, sauf si elle
     de l'audio. Une boucle inaudible maintient la page vivante écran éteint, et ce même flux audio est
     ce qui rend navigator.mediaSession — donc les boutons du casque — opérant. L'audio n'est pas une
     astuce greffée : c'est la fonction principale de la page.                 
     Ce qui en découle, sans détour sur ce qui ne marchera pas :

     ┌──────────────────┬──────────────────────────────────────────────────────    Besoin      │ A                       │
     ├──────────────────┼──────────────────────────────────────────────────────age vivante     │ dioContext              │
     │ écran éteint     │                                                      │
     ├──────────────────┼──────────────────────────────────────────────────────┤
     │ watchPosition    │ le seul inconnu — tient tant que la page vit, mais   ontinu          │ batterie OEM peuvent    │
     │                  │ l'étrangler                                          ────────────────┼─────────────────────────┤
     │ Annonces vocales │ speechSynthesis, repli MP3 pré-enregistrés           │
     ├──────────────────┼──────────────────────────────────────────────────────┤
     │ Bouton casque /  │ navigator.mediaSession.setActionHandler              cran verrouillé │                         │
     ├──────────────────┼──────────────────────────────────────────────────────ommandes        │ roid interdit le micro  │
     │ vocales au micro │ en arrière-plan                                      │
     └──────────────────┴──────────────────────────────────────────────────────┘
                                                                               se 1 — Prouver le re la feature
                                                                                négociable et à fable qui, pendant dixminutes dans une poche,
     journalise dans localStorage : chaque watchPosition, chaque tick d'horloge, l'état                                                                    ument.visibilitySteffectivement parlé.
                                                                               e doit tourner en 2.168.x.x ne l'est pas,Chrome refusera la
     géolocalisation. Deux voies, dans l'ordre :

     1. adb reverse tcp:4200 tcp:4200 puis ng serve — le téléphone voit http://localhost:4200, qui                                             est un contexte sé
     2. À défaut, une route labo-gps déployée sur le site HTTPS, installée en PWA, retirée ensuite.
        Plus fidèle au réel, mais ça met une page morte en production le temps du test.
                                                                               verdict conditionn

     - GPS survit → phase 2 telle quelle.
     - GPS étranglé mais la page vit → réduire la fréquence attendue et lisser 'allure sur une
       fenêtre plus large ; la feature tient toujours.                         a page meurt → repuest('screen'), écranallumé toute la sortie.
       Ce n'est plus la promesse initiale, et c'est à ce moment-là qu'il faudra rouvrir la question de                                                  'app Android.
                                                                               se 2 — Le tracker

     Backend
                                                                               dioType gagne COURchiron-back/.../seance/model/CardioType.java. Le switch                   CardioCalorieServie compilateur imposera de le traiter : dériver
     l'allure de la distance et de la durée, puis réutiliser runningMet (l'équation ACSM déjà en place).                                          RSE reste tel quelre + pente sans distance.
                                                                               ration V56__add_coa dernière appliquée) :

     - une ligne exercice_definition : external_id = 'cardio_outdoor_running',
       cardio_type = 'COURSE_EXTERIEUR', type_equipement = 'CARDIO', muscle_principal = 'CARDIO' —
       même forme que les quatre lignes de V32__add_cardio.sql:13-19 ;         a table course_trats JSONB NOT NULL,nb_points,
       distance_m, duree_s, denivele_positif_m, splits JSONB, created_at ;
     - ALTER TABLE serie ADD COLUMN course_trace_id BIGINT REFERENCES          ourse_trace(id).
                                                                               rquoi une trace aus le payload. La pageCourse finit avant que
     la séance soit sauvegardée, et ProgrammeServiceImpl recrée les lignes Exercice à chaque
     enregistrement — l'id d'exercice n'existe pas encore et n'est pas stable. Pire, session.ts:494-504                                                  te le payload deuxodèle). Faire voyager 150 ko de points dans ce
     payload les écrirait en double sur un modèle de programme. La trace part donc seule, avant, et seul                                                 id circule.
                                                                               veau domaine courspersistence/ service/,comme partout :

     - POST /api/courses/traces reçoit les points, les persiste, et recalcule  ôté serveur distan
       (haversine), durée, dénivelé positif et splits au kilomètre. Le client  alcule déjà tout ç
       l'affichage direct, mais la valeur qui compte est celle du serveur : un bug d'affichage ne doit pas
       polluer le journal. Renvoie { id, distanceM, dureeS, allureMoyenneKmh,  plits }.
     - GET /api/courses/traces/{id} pour le rendu ultérieur, avec contrôle de  ropriétaire à la m
       il n'y a pas de @PreAuthorize dans ce projet.
     - Vérifier que /api/courses/** correspond bien à une règle de security/SecurityConfig.java, sinon                                     'endpoint répond 4
                                                                               grammeServiceImpl e serie.courseTracedepuis
     SerieDto.courseTraceId, en refusant une trace qui n'appartient pas à l'utilisateur. SerieDto                                                   ne un Long courseTper le projette auretour.                                                                   
     Frontend

     Route calquée sur CINDY (app.routes.ts:67-71) : session/:id/course/:exoId,dComponent
     paresseux, canActivate: [authGuard].                                      
     components/course/ — course.ts/html/css/spec.ts, standalone, pas de suffixe Component,
     templateUrl + styleUrl. Le composant lit son exercice depuis              iveSessionService
     résultat dans series[0], exactement comme wod.ts:185-198.                 
     Quatre unités séparées, parce que chacune est testable seule et qu'aucune n'a sa place dans un
     composant :                                                               
     ┌─────────────────────────────┬───────────────────────────────────────────
     │           Fichier           │                    Rôle                    │
     ├─────────────────────────────┼───────────────────────────────────────────
     │                             │ watchPosition, filtrage, haversine,        │                                                                         ervice/course-trac/ dureeS /│
     │                             │ allureCourante / allureMoyenne / splits,   │
     │                             │ snapshot localStorage                      │
     ├─────────────────────────────┼────────────────────────────────────────────┤
     │                             │ la boucle inaudible qui empêche le gel,   
     │ util/audio-keepalive.ts     │ sur le motif unlockAudio() de             
     │                             │ util/escargophone.ts:24-28                 │
     ├─────────────────────────────┼───────────────────────────────────────────
     │ util/voix.ts                │ parler() via speechSynthesis, file        
     │                             │ d'attente, repli MP3                       │
     ├─────────────────────────────┼───────────────────────────────────────────
     │ util/telecommande-casque.ts │ les mediaSession.setActionHandler         
     └─────────────────────────────┴────────────────────────────────────────────┘
                                                                               is leçons de wod.telles sont déjà payées :
                                                                               Ne jamais décrémenla durée depuis startedAt à chaque tick
        (wod.ts:57-61). Un setInterval bridé en arrière-plan ne fausse alors rien.                                                                  Observer, ne pas plong pour une annonce. On teste les seuils à
        chaque tick, et une annonce ratée pendant une absence est consommée en silence
        (diffuserAnnonces, wod.ts:126-140).
     3. Débloquer l'audio dans le geste utilisateur — l'AudioContext se crée au tap sur « Démarrer »,
        sans quoi tout est muet une demi-heure plus tard.

     Filtrage des points : rejeter une position dont accuracy > 25 m, et ignorer un déplacement de
     moins de 3 m — sans ça la dérive GPS à l'arrêt gonfle la distance. L'allurrante se lit sur u
     fenêtre glissante de 30 secondes ; l'allure instantanée est trop bruitée  r être annoncée.

     Le snapshot localStorage (clé chiron.course, motif de wod.ts:205-244) doit stocker les                                                               nts arrondis à 5 docalStorage tient environ 5 Mo et une heure de
     course à 1 Hz s'en approche.                                              
     Coaching vocal : allure cible en min/km, réglable avant et pendant. Au-delà d'un écart maintenu
     plusieurs secondes, une annonce, puis un silence imposé avant la suivante — un coach qui répète tous
     les cinq secondes se fait couper. Une annonce à chaque kilomètre franchi.

     Commandes, les deux voies que tu as demandées :
                                                                               cran allumé — un gh-to-talk, reprenantwebkitSpeechRecognition                                                 t sa chaîne de cores decomponents/chat/chat.ts:150+. Le bouton
       copie l'ergonomie de la cible du WOD : (pointerdown), touch-action: manipulation,                                                           ser-select: none (
     - Écran éteint — mediaSession : play/pause met la course en pause et la reprend, nexttrack                                                      ccélère l'allure cit. Chaque action estconfirmée à la voix,
       sinon l'utilisateur n'a aucun retour, poche fermée.

     Carte d'exercice : isCourse() sur cardioType === 'COURSE_EXTERIEUR', et une paire
     @Input() canStartCourse / @Output() startCourse câblée uniquement depuis session.html, pas
     depuis programme-builder.html — c'est ce qui empêche de lancer une course éditant un program
     (session.html:55-56 pour le WOD). Les champs cardio deviennent lecture    le pour ce type :
     le tracker qui les remplit.

     i18n : namespace course.*, chaque clé dans i18n/fr.ts et i18n/en.ts, fr faisant foi.
                                                                               ts

     - Backend : CardioCalorieServiceTest gagne les cas COURSE_EXTERIEUR ; un
       CourseTraceServiceTest couvre haversine, splits et dénivelé sur une traconnue ; le contrôla dans course/contt mvn verify, pas mvntest.
     - Frontend : course.spec.ts sur le modèle de wod.spec.ts (336 lignes, faux timers, Router,                                                         ctiveSessionServictubbernavigator.geolocation,
       speechSynthesis, AudioContext et mediaSession. Couvrir en priorité le   etour d'arrière-pl
       — c'est le cas qui casse : distance recalculée juste, annonces manquées non rejouées.

     Phase 3 — Le rendu du tracé

     Tracé SVG sans fond de carte : projection équirectangulaire (suffisante à chelle d'une sorti
     mise à l'échelle sur la bounding box, <path> segmenté et coloré par allure. Zéro dépendance
     externe, fonctionne hors ligne, rien à héberger. Affiché à la fin de la   rse et dans le jou
     côté des splits.                                                          
     Vérification

     Backend

     cd chiron-back                                                             spotless:apply
     mvn test                                                                   verify

     Frontend
                                                                               chiron-front
     npx tsc --noEmit
     npm test                                                                   run build

     Bout en bout, la seule qui compte                                         
     1. Phase 1 : dix minutes en poche, écran éteint, puis lire le journal du spike. C'est ce relevé qui
        valide ou invalide toute l'approche.                                   Sortie réelle de véglée au départ, vérifier que les annonces tombent,                                              que le bouton du c que la distance finaleest cohérente avec un                                                  parcours connu.Retour à la séancee, distance et allure ;le journal affiche
        sortie ; SanteActivite est planifiée pour l'enrichissement comme pour
        toute séance termi

     Écueils connus

     - Gestionnaires de baet Huawei tuent lesprocessus en arrière-plan
       quoi qu'en dise le clue de l'optimisation de
       batterie, et ça se
       la main dans les ré
     - SeanceResumeServicejourd'hui, décrit toute
       série comme
       « N reps @ X,Xkg » a donc à Noctua comme 0reps @ 0,0kg. À traiter                                                 quand la course proètre ici.ProgrammeServiceImpar défaut et l'estimateurle réinterprètesilencieusement en rse seront fausses pourqui n'a pas renseig
       poids.
