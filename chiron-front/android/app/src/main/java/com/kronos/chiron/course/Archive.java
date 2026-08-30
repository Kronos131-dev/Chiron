package com.kronos.chiron.course;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

// WHY: le service s'arrête quelques secondes après la fin de course, et la page peut n'être
// rouverte que bien plus tard. Une sortie close par la voix ou par le bouton de la notification
// n'avait alors plus aucun porteur : le service mort, les points n'existaient nulle part et le
// journal héritait d'une course sans parcours. Ils attendent donc sur le disque.
final class Archive {

    private static final String FICHIER = "course-terminee.json";
    private static final int TAMPON = 8192;

    private Archive() {}

    static void ecrire(Context context, JSONObject contenu) {
        try (FileOutputStream sortie = new FileOutputStream(fichier(context))) {
            sortie.write(contenu.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignore) {}
    }

    static JSONObject lire(Context context) {
        File source = fichier(context);
        if (!source.exists()) return null;
        try (FileInputStream entree = new FileInputStream(source)) {
            ByteArrayOutputStream accumule = new ByteArrayOutputStream();
            byte[] tampon = new byte[TAMPON];
            int lus;
            while ((lus = entree.read(tampon)) != -1) accumule.write(tampon, 0, lus);
            return new JSONObject(accumule.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception erreur) {
            effacer(context);
            return null;
        }
    }

    static void effacer(Context context) {
        try {
            File source = fichier(context);
            if (source.exists()) source.delete();
        } catch (Exception ignore) {}
    }

    private static File fichier(Context context) {
        return new File(context.getFilesDir(), FICHIER);
    }
}
