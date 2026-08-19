package com.kronos.chiron.sante.service;

import com.kronos.chiron.fitbit.client.GoogleHealthDataType;
import tools.jackson.databind.JsonNode;

public interface SanteDiagnosticService {

    JsonNode capturerBrut(String chironUsername, GoogleHealthDataType type, int jours);

    JsonNode sonderSlug(String chironUsername, String slug, int jours);
}
