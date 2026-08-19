package com.kronos.chiron.fitbit.client;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GoogleHealthParser {

    private GoogleHealthParser() {
    }

    public static String nextPageToken(JsonNode response) {
        if (response == null) return null;
        JsonNode token = response.get("nextPageToken");
        return token != null && token.isTextual() && !token.asText().isBlank() ? token.asText() : null;
    }

    public static Map<LocalDate, Double> dailyRollupByDate(JsonNode rollUpResponse, String valueNodeField,
            String... sumFieldCandidates) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (rollUpResponse == null) return result;
        JsonNode points = rollUpResponse.get("rollupDataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            LocalDate date = FitbitParser.googleDate(pt.get("civilStartTime"));
            if (date == null) continue;
            JsonNode value = pt.get(valueNodeField);
            Long sum = FitbitParser.longField(value, sumFieldCandidates);
            if (sum == null) sum = FitbitParser.firstLong(value);
            if (sum != null) {
                result.merge(date, sum.doubleValue(), Double::sum);
            }
        }
        return result;
    }

    public record ZoneMinutes(Integer bruleuse, Integer cardio, Integer pic) {
    }

    public static Map<LocalDate, ZoneMinutes> zoneMinutesByDate(JsonNode rollUpResponse) {
        Map<LocalDate, int[]> acc = new HashMap<>();
        if (rollUpResponse != null) {
            JsonNode points = rollUpResponse.get("rollupDataPoints");
            if (points != null && points.isArray()) {
                for (JsonNode pt : points) {
                    LocalDate date = FitbitParser.googleDate(pt.get("civilStartTime"));
                    if (date == null) continue;
                    for (JsonNode entry : zonesDe(pt.get("timeInHeartRateZone"))) {
                        JsonNode zoneNode = entry.get("heartRateZone");
                        if (zoneNode == null) zoneNode = entry.get("zoneType");
                        String zone = zoneNode != null ? zoneNode.asText(null) : null;
                        Long seconds = dureeEnSecondes(entry);
                        if (zone == null || seconds == null) continue;
                        int minutes = (int) Math.round(seconds / 60.0);
                        int[] bucket = acc.computeIfAbsent(date, d -> new int[3]);
                        String z = zone.toUpperCase(java.util.Locale.ROOT);
                        if (z.contains("PEAK")) bucket[2] += minutes;
                        else if (z.contains("VIGOROUS") || z.contains("CARDIO")) bucket[1] += minutes;
                        else if (z.contains("MODERATE") || z.contains("FAT_BURN") || z.contains("FATBURN")) {
                            bucket[0] += minutes;
                        }
                    }
                }
            }
        }
        Map<LocalDate, ZoneMinutes> result = new HashMap<>();
        acc.forEach((d, b) -> result.put(d, new ZoneMinutes(b[0], b[1], b[2])));
        return result;
    }

    // WHY: Google emballe les zones dans un objet timeInHeartRateZone qui contient à son
    // tour un tableau timeInHeartRateZones. Ce niveau intermédiaire était ignoré : l'objet
    // enveloppe était pris pour une entrée de zone, ne portait aucun heartRateZone, et
    // chaque journée ressortait donc sans la moindre minute.
    private static Iterable<JsonNode> zonesDe(JsonNode valeur) {
        if (valeur == null) return List.of();
        if (valeur.isArray()) return valeur;
        JsonNode imbrique = valeur.get("timeInHeartRateZones");
        if (imbrique != null && imbrique.isArray()) return imbrique;
        return List.of(valeur);
    }

    // WHY: la durée arrive au format Duration de protobuf, « 47520s ». longField tente un
    // Long.parseLong qui lève sur le suffixe et renvoie null, ce qui suffisait à vider
    // toutes les zones même une fois l'imbrication franchie.
    private static Long dureeEnSecondes(JsonNode entree) {
        for (String nom : new String[]{"durationSum", "duration", "seconds"}) {
            JsonNode champ = entree.get(nom);
            if (champ == null || champ.isNull() || champ.isObject() || champ.isArray()) continue;
            if (champ.isNumber()) return champ.asLong();
            String texte = champ.asText().trim();
            if (texte.endsWith("s")) texte = texte.substring(0, texte.length() - 1);
            try {
                return (long) Double.parseDouble(texte);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public record FcBucket(Instant debut, Integer min, Double moyenne, Integer max, Integer nbEchantillons) {
    }

    public static List<FcBucket> heartRateBuckets(JsonNode rollUpResponse) {
        List<FcBucket> result = new ArrayList<>();
        if (rollUpResponse == null) return result;
        JsonNode points = rollUpResponse.get("rollupDataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode startNode = pt.get("startTime");
            if (startNode == null || !startNode.isTextual()) continue;
            Instant debut;
            try {
                debut = Instant.parse(startNode.asText());
            } catch (RuntimeException e) {
                continue;
            }
            JsonNode hr = pt.get("heartRate");
            if (hr == null) continue;
            Long min = FitbitParser.longField(hr, "beatsPerMinuteMin", "min", "minBeatsPerMinute", "bpmMin");
            Long avg = FitbitParser.longField(hr, "beatsPerMinuteAvg", "beatsPerMinuteMean", "avg", "average",
                    "mean", "bpmAvg");
            Long max = FitbitParser.longField(hr, "beatsPerMinuteMax", "max", "maxBeatsPerMinute", "bpmMax");
            Long count = FitbitParser.longField(hr, "sampleCount", "count", "numSamples");
            if (avg == null) avg = FitbitParser.firstLong(hr);
            if (min == null && max == null && avg == null) continue;
            result.add(new FcBucket(debut, min != null ? min.intValue() : null,
                    avg != null ? avg.doubleValue() : null, max != null ? max.intValue() : null,
                    count != null ? count.intValue() : null));
        }
        return result;
    }

    public record VfcJour(LocalDate date, Double vfcMs, Double vfcSommeilProfondMs) {
    }

    public static Map<LocalDate, VfcJour> hrvByDate(JsonNode listResponse) {
        Map<LocalDate, VfcJour> result = new HashMap<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode hrv = pt.get("dailyHeartRateVariability");
            if (hrv == null) continue;
            LocalDate date = dataPointDate(pt, hrv);
            if (date == null) continue;
            Double avg = doubleField(hrv, "averageHeartRateVariabilityMilliseconds", "averageHrvMilliseconds",
                    "value");
            Double deepSleep = doubleField(hrv, "deepSleepRootMeanSquareOfSuccessiveDifferencesMilliseconds",
                    "deepSleepRmssdMilliseconds");
            result.put(date, new VfcJour(date, avg, deepSleep));
        }
        return result;
    }

    public record Vo2MaxJour(LocalDate date, Double vo2Max, String niveauAptitude) {
    }

    public static Map<LocalDate, Vo2MaxJour> vo2MaxByDate(JsonNode listResponse) {
        Map<LocalDate, Vo2MaxJour> result = new HashMap<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode vo2 = charteUtile(pt, "dailyVo2Max");
            if (vo2 == null) continue;
            LocalDate date = dataPointDate(pt, vo2);
            if (date == null) continue;
            Double value = doubleField(vo2, "vo2Max", "value");
            if (value == null) value = premierNombrePlausible(vo2);
            JsonNode niveauNode = vo2.get("cardioFitnessLevel");
            String niveau = niveauNode != null && niveauNode.isTextual()
                    ? niveauNode.asText()
                    : premierTexte(vo2);
            if (value == null && niveau == null) continue;
            result.put(date, new Vo2MaxJour(date, value, niveau));
        }
        return result;
    }

    // WHY: le nom du noeud qui porte la valeur est deduit du slug de l'API et n'a jamais
    // ete confronte a un compte reel. Quand il ne correspond pas, on retombe sur le seul
    // objet du point de donnee qui porte une date : c'est structurellement la charge utile.
    private static JsonNode charteUtile(JsonNode dataPoint, String champAttendu) {
        JsonNode attendu = dataPoint.get(champAttendu);
        if (attendu != null && attendu.isObject()) return attendu;
        for (Map.Entry<String, JsonNode> champ : dataPoint.properties()) {
            JsonNode valeur = champ.getValue();
            if (valeur.isObject() && (valeur.has("date") || valeur.has("civilDate"))) return valeur;
        }
        return null;
    }

    // WHY: borne physiologique du VO2max humain. Sans elle un repli generique pourrait
    // retenir n'importe quel nombre du JSON — un identifiant, un compte d'echantillons.
    private static final double VO2MAX_MIN = 10.0;
    private static final double VO2MAX_MAX = 100.0;

    private static Double premierNombrePlausible(JsonNode noeud) {
        for (Map.Entry<String, JsonNode> champ : noeud.properties()) {
            JsonNode valeur = champ.getValue();
            if (!valeur.isNumber()) continue;
            double d = valeur.asDouble();
            if (d >= VO2MAX_MIN && d <= VO2MAX_MAX) return d;
        }
        return null;
    }

    private static String premierTexte(JsonNode noeud) {
        for (Map.Entry<String, JsonNode> champ : noeud.properties()) {
            JsonNode valeur = champ.getValue();
            if (valeur.isTextual() && !valeur.asText().isBlank()) return valeur.asText();
        }
        return null;
    }

    public static Map<LocalDate, Double> dailyDoubleByDate(JsonNode listResponse, String parentField,
            String... valueFieldCandidates) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode value = pt.get(parentField);
            if (value == null) continue;
            LocalDate date = dataPointDate(pt, value);
            if (date == null) continue;
            Double v = doubleField(value, valueFieldCandidates);
            if (v != null) result.put(date, v);
        }
        return result;
    }

    // WHY: le modèle de stades de Google Health ne connaît que AWAKE, LIGHT, DEEP et REM ;
    // il n'expose aucun stade RESTLESS, propre au Fitbit classique. L'agitation que
    // l'application affiche est le temps passé au lit que les stades n'expliquent pas —
    // ni sommeil, ni éveil déclaré, ni endormissement, ni traîne au réveil. On le
    // reconstitue donc par différence plutôt que de le laisser nul.
    private static final Set<String> STADES_RECONNUS = Set.of("DEEP", "LIGHT", "REM", "AWAKE");

    // WHY: chaque stade est arrondi à la minute, si bien que leur somme dépasse
    // couramment l'intervalle de quelques minutes — une nuit réelle donnait 533 pour 532.
    // Une entrée agrégée, elle, recompte tout le sommeil et fait presque doubler le total :
    // la marge d'un quart sépare les deux cas sans ambiguïté.
    private static final double TOLERANCE_SOMME_STADES = 1.25;

    // WHY: Google publie l'éveil et l'agitation comme deux stades distincts — 21 min et
    // 18 min sur une même nuit — mais le nom du second n'est pas RESTLESS et n'a jamais
    // pu être observé. Plutôt que de le deviner, on somme tout stade qui n'est ni sommeil
    // ni éveil : quel que soit son nom, c'est l'agitation.
    //
    // Le total des stades ne doit pas dépasser le temps passé au lit : au-delà, l'entrée
    // inconnue n'est pas un stade mais un agrégat (un « ASLEEP » qui recompte le sommeil,
    // par exemple), et la somme serait absurde. On rend alors la main au calcul par
    // différence.
    private static Integer agitationDesStadesNonReconnus(Instant debut, Instant fin,
            Map<String, Integer> parStade) {
        int total = 0;
        int somme = 0;
        boolean trouve = false;
        for (Map.Entry<String, Integer> stade : parStade.entrySet()) {
            int minutes = nzInt(stade.getValue());
            somme += minutes;
            if (STADES_RECONNUS.contains(stade.getKey().toUpperCase(java.util.Locale.ROOT))) continue;
            total += minutes;
            trouve = true;
        }
        if (!trouve) return null;
        if (debut != null && fin != null) {
            long minutesAuLit = Duration.between(debut, fin).toMinutes();
            if (somme > minutesAuLit * TOLERANCE_SOMME_STADES) return null;
        }
        return total;
    }

    private static int nzInt(Integer valeur) {
        return valeur != null ? valeur : 0;
    }

    private static Integer agitationResiduelle(Instant debut, Instant fin, Map<String, Integer> parStade,
            Integer minutesEveille, Integer minutesAvant, Integer minutesApres) {
        if (debut == null || fin == null || parStade.isEmpty()) return null;
        long minutesAuLit = Duration.between(debut, fin).toMinutes();
        if (minutesAuLit <= 0) return null;

        long expliquees = nz(parStade.get("DEEP")) + nz(parStade.get("LIGHT")) + nz(parStade.get("REM"))
                + nz(minutesEveille) + nz(minutesAvant) + nz(minutesApres);
        long residu = minutesAuLit - expliquees;
        return residu > 0 ? (int) residu : 0;
    }

    private static long nz(Integer valeur) {
        return valeur != null ? valeur : 0L;
    }

    public record SommeilBrut(String externalId, Instant debut, Instant fin, LocalDate date, boolean sieste,
            boolean stadesDisponibles, Integer minutesEndormi, Integer minutesEveille,
            Integer minutesAvantEndormissement, Integer minutesApresReveil, Integer minutesProfond,
            Integer minutesLeger, Integer minutesParadoxal, Integer minutesAgite, Integer nbReveils) {
    }

    public static List<SommeilBrut> sleepSessions(JsonNode listResponse) {
        List<SommeilBrut> result = new ArrayList<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode sleep = pt.get("sleep");
            if (sleep == null) continue;
            JsonNode interval = sleep.get("interval");
            Instant debut = instantField(interval, "startTime");
            Instant fin = instantField(interval, "endTime");
            if (debut == null && fin == null) continue;
            LocalDate date = fin != null ? asLocalDate(fin) : asLocalDate(debut);

            JsonNode metadata = sleep.get("metadata");
            String externalId = metadata != null && metadata.hasNonNull("externalId")
                    ? metadata.get("externalId").asText()
                    : null;
            boolean sieste = metadata != null && metadata.path("nap").asBoolean(false);
            boolean stadesDisponibles = metadata != null
                    && "SUCCEEDED".equals(metadata.path("stagesStatus").asText(null));

            JsonNode summary = sleep.get("summary");
            Integer minutesEndormi = intField(summary, "minutesAsleep");
            Integer minutesEveilleSummary = intField(summary, "minutesAwake");
            Integer minutesAvant = intField(summary, "minutesToFallAsleep");
            Integer minutesApres = intField(summary, "minutesAfterWakeUp");

            Map<String, Integer> parStade = new HashMap<>();
            Integer nbReveils = null;
            if (summary != null) {
                JsonNode stagesSummary = summary.get("stagesSummary");
                if (stagesSummary != null && stagesSummary.isArray()) {
                    for (JsonNode s : stagesSummary) {
                        String type = s.path("type").asText(null);
                        Integer minutes = intField(s, "minutes");
                        if (type == null || minutes == null) continue;
                        parStade.merge(type, minutes, Integer::sum);
                        if ("AWAKE".equals(type)) {
                            Integer count = intField(s, "count");
                            if (count != null) nbReveils = count;
                        }
                    }
                }
            }

            Integer minutesEveille = minutesEveilleSummary != null
                    ? minutesEveilleSummary
                    : parStade.get("AWAKE");
            Integer minutesAgite = agitationDesStadesNonReconnus(debut, fin, parStade);
            if (minutesAgite == null) {
                minutesAgite = agitationResiduelle(debut, fin, parStade, minutesEveille, minutesAvant,
                        minutesApres);
            }

            result.add(new SommeilBrut(externalId, debut, fin, date, sieste, stadesDisponibles,
                    minutesEndormi, minutesEveille,
                    minutesAvant, minutesApres, parStade.get("DEEP"), parStade.get("LIGHT"), parStade.get("REM"),
                    minutesAgite, nbReveils));
        }
        return result;
    }

    private static LocalDate dataPointDate(JsonNode dataPoint, JsonNode value) {
        LocalDate d = FitbitParser.googleDate(value.get("date"));
        if (d != null) return d;
        return FitbitParser.googleDate(dataPoint.get("date"));
    }

    private static Double doubleField(JsonNode obj, String... names) {
        if (obj == null) return null;
        for (String n : names) {
            JsonNode f = obj.get(n);
            if (f == null || f.isNull() || f.isObject() || f.isArray()) continue;
            if (f.isNumber()) return f.asDouble();
            try {
                return Double.parseDouble(f.asText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Integer intField(JsonNode obj, String name) {
        Long l = FitbitParser.longField(obj, name);
        return l != null ? l.intValue() : null;
    }

    private static Instant instantField(JsonNode obj, String name) {
        if (obj == null) return null;
        JsonNode f = obj.get(name);
        if (f == null || !f.isTextual()) return null;
        try {
            return Instant.parse(f.asText());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate asLocalDate(Instant instant) {
        return instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
