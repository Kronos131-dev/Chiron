package com.kronos.chiron.visbody.service;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.visbody.dto.VisbodyReport;

public interface VisbodyImportService {

    enum Outcome {
        IMPORTED, DUPLICATE, USER_NOT_FOUND, INVALID
    }

    record ImportResult(Outcome outcome, String detail) {
    }

    ImportResult importForUser(byte[] pdf, Utilisateur user);

    ImportResult importFromEmail(byte[] pdf, String senderEmail);

    ImportResult persist(VisbodyReport report, Utilisateur user, String source);
}
