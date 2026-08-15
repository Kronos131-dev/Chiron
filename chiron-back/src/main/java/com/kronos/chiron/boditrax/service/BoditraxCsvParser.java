package com.kronos.chiron.boditrax.service;

import com.kronos.chiron.visbody.dto.VisbodyReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class BoditraxCsvParser {

    private static final Logger log = LoggerFactory.getLogger(BoditraxCsvParser.class);

    private static final DateTimeFormatter US_DATETIME = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US);

    private static final String SEC_USER = "User Details";
    private static final String SEC_PHYSIQUE = "User Physique Details";
    private static final String SEC_SCAN = "User Scan Details";
    private static final String SEC_LOGIN = "User Login Details";

    public record ParsedBoditrax(List<VisbodyReport> scans, String gender,
            Double tailleCm, LocalDate dateNaissance) {
    }

    public ParsedBoditrax parse(byte[] csv) {
        String content = new String(csv, StandardCharsets.UTF_8);
        String[] lines = content.split("\r?\n");

        String gender = null;
        LocalDate dateNaissance = null;
        Double heightCm = null;
        Map<LocalDateTime, Map<String, Double>> byScan = new LinkedHashMap<>();

        String section = null;
        boolean skipHeader = false;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            String titre = stripBom(line);
            if (titre.equals(SEC_USER) || titre.equals(SEC_PHYSIQUE)
                    || titre.equals(SEC_SCAN) || titre.equals(SEC_LOGIN)) {
                section = titre;
                skipHeader = true;
                continue;
            }
            if (section == null) continue;
            if (skipHeader) {
                skipHeader = false;
                continue;
            }

            String[] f = line.split(",", -1);
            switch (section) {
                case SEC_USER -> {
                    // WHY: Email,FirstName LastName,DateOfBirth,Gender — le nom peut contenir
                    // une virgule ; le genre est toujours en dernier, la date juste avant.
                    if (f.length >= 2) {
                        gender = f[f.length - 1].trim();
                        LocalDateTime dob = tryDateTime(f[f.length - 2].trim());
                        if (dob != null) dateNaissance = dob.toLocalDate();
                    }
                }
                case SEC_PHYSIQUE -> {
                    if (f.length >= 1) {
                        Double h = tryDouble(f[0]);
                        if (h != null) heightCm = h;
                    }
                }
                case SEC_SCAN -> {
                    if (f.length >= 3) {
                        String metric = f[0].trim();
                        Double value = tryDouble(f[1]);
                        LocalDateTime when = tryDateTime(f[2].trim());
                        if (when != null && value != null && !metric.isEmpty()) {
                            byScan.computeIfAbsent(when, k -> new LinkedHashMap<>()).put(metric, value);
                        }
                    }
                }
                default -> {
                }
            }
        }

        List<VisbodyReport> scans = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<String, Double>> e : byScan.entrySet()) {
            scans.add(toReport(e.getKey(), e.getValue(), heightCm, gender));
        }
        log.info("Boditrax : {} scan(s) parsé(s).", scans.size());
        return new ParsedBoditrax(scans, gender, heightCm, dateNaissance);
    }

    private VisbodyReport toReport(LocalDateTime when, Map<String, Double> m,
            Double heightCm, String gender) {
        VisbodyReport r = new VisbodyReport();
        r.setMesureLe(when);

        Double bw = m.get("BodyWeight");
        r.setPoids(bw);
        r.setMasseMusculaire(m.get("MuscleMass"));
        r.setMgc(m.get("FatMass"));
        r.setMmc(m.get("FatFreeMass"));
        r.setImc(m.get("BodyMassIndex"));
        r.setGraisseViscerale(m.get("VisceralFatRating"));
        r.setEauTotale(m.get("WaterMass"));
        r.setEauIntra(m.get("IntraCellularWaterMass"));
        r.setEauExtra(m.get("ExtraCellularWaterMass"));
        r.setAgeMetabolique(m.get("MetabolicAge"));
        r.setSelInorganique(m.get("BoneMass"));

        Double score = m.get("BoditraxScore");
        if (score != null) r.setNote((int) Math.round(score));

        r.setMgcBrasGauche(m.get("LeftArmFatMass"));
        r.setMgcBrasDroit(m.get("RightArmFatMass"));
        r.setMgcTronc(m.get("TrunkFatMass"));
        r.setMgcJambeGauche(m.get("LeftLegFatMass"));
        r.setMgcJambeDroite(m.get("RightLegFatMass"));
        r.setMuscleBrasGauche(m.get("LeftArmMuscleMass"));
        r.setMuscleBrasDroit(m.get("RightArmMuscleMass"));
        r.setMuscleTronc(m.get("TrunkMuscleMass"));
        r.setMuscleJambeGauche(m.get("LeftLegMuscleMass"));
        r.setMuscleJambeDroite(m.get("RightLegMuscleMass"));

        Double fat = m.get("FatMass");
        if (fat != null && bw != null && bw > 0) r.setTgcPct(fat / bw * 100.0);
        Double kj = m.get("BasalMetabolicRatekJ");
        if (kj != null) r.setMbKcal(kj / 4.184);
        Double ecw = m.get("ExtraCellularWaterMass");
        Double tbw = m.get("WaterMass");
        if (ecw != null && tbw != null && tbw > 0) r.setRatioEcwTbw(ecw / tbw);

        // WHY: la masse musculaire squelettique est reconstruite depuis l'indice SMI, parce que
        // la jauge MMS recalcule SMI = mms / taille² et doit retomber sur la valeur Boditrax.
        Double smi = m.get("SarcopeniaSMI");
        Double hCm = heightCm != null ? heightCm : m.get("Height");
        if (smi != null && hCm != null && hCm > 0) {
            double hm = hCm / 100.0;
            r.setMms(smi * hm * hm);
        }

        r.setTailleCm(hCm);
        Double age = m.get("Age");
        if (age != null) r.setAge((int) Math.round(age));
        r.setSexe(gender);
        return r;
    }

    private static String stripBom(String s) {
        return s.isEmpty() ? s : s.replace("﻿", "");
    }

    private static Double tryDouble(String s) {
        if (s == null) return null;
        String v = s.trim().replace("\"", "");
        if (v.isEmpty()) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime tryDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        // WHY: Boditrax sépare l'heure de AM/PM par une espace fine insécable (U+202F) et peut
        // utiliser une espace insécable (U+00A0) : java \s ne couvre ni l'une ni l'autre, il
        // faut donc les normaliser en espace simple avant le parsing.
        String v = s.trim().replace("\"", "")
                .replace(' ', ' ')
                .replace(' ', ' ')
                .replaceAll("\\s+", " ");
        try {
            return LocalDateTime.parse(v, US_DATETIME);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
