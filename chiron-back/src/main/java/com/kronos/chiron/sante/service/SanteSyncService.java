package com.kronos.chiron.sante.service;

public interface SanteSyncService {

    void ensureBackfillAsync(String chironUsername);

    void syncRecent(String chironUsername, int jours);
}
