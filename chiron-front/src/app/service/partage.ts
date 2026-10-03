import { estNatif } from './plateforme';

export type IssuePartage = 'partage' | 'telechargement' | 'annule';

function estAnnulation(erreur: unknown): boolean {
  if (erreur instanceof DOMException && erreur.name === 'AbortError') return true;
  return erreur instanceof Error && /cancel/i.test(erreur.message);
}

function telecharger(fichier: File): void {
  const adresse = URL.createObjectURL(fichier);
  const lien = document.createElement('a');
  lien.href = adresse;
  lien.download = fichier.name;
  lien.click();
  setTimeout(() => URL.revokeObjectURL(adresse), 1000);
}

function enBase64(fichier: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const lecteur = new FileReader();
    lecteur.onload = () => resolve(String(lecteur.result).split(',')[1] ?? '');
    lecteur.onerror = () => reject(lecteur.error);
    lecteur.readAsDataURL(fichier);
  });
}

async function partagerSurLeWeb(fichier: File, texte: string): Promise<IssuePartage> {
  if (navigator.canShare?.({ files: [fichier] })) {
    try {
      await navigator.share({ files: [fichier], text: texte });
      return 'partage';
    } catch (erreur) {
      if (estAnnulation(erreur)) return 'annule';
    }
  }
  telecharger(fichier);
  navigator.clipboard?.writeText(texte).catch(() => {});
  return 'telechargement';
}

async function partagerSurAndroid(fichier: File, texte: string): Promise<IssuePartage> {
  const [{ Directory, Filesystem }, { Share }] = await Promise.all([
    import('@capacitor/filesystem'),
    import('@capacitor/share'),
  ]);
  const ecrit = await Filesystem.writeFile({
    path: fichier.name,
    data: await enBase64(fichier),
    directory: Directory.Cache,
  });
  try {
    await Share.share({ text: texte, files: [ecrit.uri] });
    return 'partage';
  } catch (erreur) {
    if (estAnnulation(erreur)) return 'annule';
    throw erreur;
  }
}

// WHY: Safari n'accorde `navigator.share` qu'à l'appel qui suit directement le geste de
// l'utilisateur ; le moindre `await` placé avant (polices, export du canvas) lui fait perdre
// ce droit. L'image est donc forgée à l'ouverture de l'aperçu, et cette fonction part au clic
// sans rien attendre en amont.
export function partagerImage(fichier: File, texte: string): Promise<IssuePartage> {
  return estNatif() ? partagerSurAndroid(fichier, texte) : partagerSurLeWeb(fichier, texte);
}
