import { registerPlugin } from '@capacitor/core';
import { Capacitor } from '@capacitor/core';

export interface EcouteurVocal {
  partiel?(texte: string): void;
  final(texte: string): void;
  erreur(raison: string): void;
}

export interface ReconnaissanceVocale {
  disponible(): boolean;
  demarrer(langue: string, ecouteur: EcouteurVocal): void;
  arreter(): void;
}

interface ChironVoixPlugin {
  disponible(): Promise<{ disponible: boolean }>;
  demarrer(options: { langue: string }): Promise<void>;
  arreter(): Promise<void>;
  addListener(
    evenement: 'partiel' | 'final',
    ecouteur: (donnees: { texte: string }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    evenement: 'erreur',
    ecouteur: (donnees: { raison: string }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
}

const ChironVoix = registerPlugin<ChironVoixPlugin>('ChironVoix');

class ReconnaissanceWeb implements ReconnaissanceVocale {
  private moteur: any = null;

  disponible(): boolean {
    return this.constructeur() !== null;
  }

  private constructeur(): any | null {
    if (typeof window === 'undefined') return null;
    const global = window as any;
    return global.SpeechRecognition ?? global.webkitSpeechRecognition ?? null;
  }

  demarrer(langue: string, ecouteur: EcouteurVocal): void {
    const Moteur = this.constructeur();
    if (!Moteur) {
      ecouteur.erreur('indisponible');
      return;
    }
    this.arreter();
    const moteur = new Moteur();
    moteur.lang = langue;
    moteur.continuous = false;
    moteur.interimResults = true;
    moteur.maxAlternatives = 3;

    moteur.onresult = (evenement: any) => {
      const resultat = evenement.results[evenement.results.length - 1];
      const texte = String(resultat[0].transcript);
      if (resultat.isFinal) ecouteur.final(texte);
      else ecouteur.partiel?.(texte);
    };
    moteur.onerror = (evenement: any) => ecouteur.erreur(String(evenement?.error ?? 'erreur'));
    moteur.onend = () => {
      this.moteur = null;
    };

    this.moteur = moteur;
    try {
      moteur.start();
    } catch {
      this.moteur = null;
      ecouteur.erreur('demarrage');
    }
  }

  arreter(): void {
    if (!this.moteur) return;
    try {
      this.moteur.stop();
    } catch {}
    this.moteur = null;
  }
}

class ReconnaissanceNative implements ReconnaissanceVocale {
  private abonnements: { remove: () => Promise<void> }[] = [];
  private ecouteur: EcouteurVocal | null = null;

  disponible(): boolean {
    return true;
  }

  demarrer(langue: string, ecouteur: EcouteurVocal): void {
    this.ecouteur = ecouteur;
    this.brancher().then(() =>
      ChironVoix.demarrer({ langue: langue.startsWith('en') ? 'en' : 'fr' }).catch((erreur) =>
        ecouteur.erreur(String(erreur?.message ?? erreur)),
      ),
    );
  }

  private async brancher(): Promise<void> {
    if (this.abonnements.length) return;
    this.abonnements.push(
      await ChironVoix.addListener('partiel', ({ texte }) => this.ecouteur?.partiel?.(texte)),
      await ChironVoix.addListener('final', ({ texte }) => this.ecouteur?.final(texte)),
      await ChironVoix.addListener('erreur', ({ raison }) => this.ecouteur?.erreur(raison)),
    );
  }

  arreter(): void {
    ChironVoix.arreter().catch(() => {});
  }
}

export function creerReconnaissance(): ReconnaissanceVocale {
  return Capacitor.isNativePlatform() ? new ReconnaissanceNative() : new ReconnaissanceWeb();
}
