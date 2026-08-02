package com.kronos.chiron.core.security;

import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;

import java.util.Set;

public final class AdminRule {

    private static final Set<String> OWNER_USERNAMES = Set.of("kronos", "chiron");

    private AdminRule() {
    }

    public static boolean isOwnerUsername(String username) {
        return username != null && OWNER_USERNAMES.contains(username.toLowerCase());
    }

    public static boolean isAdmin(Utilisateur user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public static boolean isAdminOrOwner(Utilisateur user) {
        return isAdmin(user) || (user != null && isOwnerUsername(user.getUsername()));
    }
}
