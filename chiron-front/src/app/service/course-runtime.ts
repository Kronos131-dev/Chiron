import { Signal } from '@angular/core';
import { CoursePointDto, CourseSplitDto } from './chiron-api';
import { EtatCourse } from './course-tracker';
import { Commande } from '../util/commandes-vocales';
import { UniteAllure } from '../util/allure';

export interface OptionsCourse {
  langue: string;
  titre: string;
  cibleMinParKm: number | null;
  phrases: Record<string, string>;
  uniteAllure: UniteAllure;
  volumeVoix: number;
  objectifDistanceM: number;
  intervalleAnnonceM: number;
  motCle: boolean;
}

export interface CourseRuntime {
  readonly natif: boolean;

  readonly etat: Signal<EtatCourse>;
  readonly points: Signal<CoursePointDto[]>;
  readonly splits: Signal<CourseSplitDto[]>;
  readonly distanceM: Signal<number>;
  readonly dureeS: Signal<number>;
  readonly allureCouranteKmh: Signal<number>;
  readonly allureMoyenneKmh: Signal<number>;
  readonly precisionM: Signal<number | null>;
  readonly erreurGps: Signal<string | null>;
  readonly signalPerdu: Signal<boolean>;
  readonly ecoute: Signal<boolean>;
  readonly transcript: Signal<string>;
  readonly audioActif: Signal<boolean>;
  readonly microDisponible: Signal<boolean>;
  readonly commandeComprise: Signal<boolean | null>;
  readonly erreurMicro: Signal<string | null>;
  readonly voixMuette: Signal<boolean>;
  readonly objectifDureeS: Signal<number>;
  readonly motCleActif: Signal<boolean>;
  readonly motCleIndisponible: Signal<string | null>;

  attacher(routineId: string, exoId: string): void;
  reprendreCourseEnCours(): Promise<boolean>;
  cibleRetenue(): number | null;
  configurer(options: OptionsCourse): void;
  demarrer(): Promise<void>;
  basculerPause(): void;
  arreter(): Promise<void>;
  fixerCible(minParKm: number | null): void;
  dire(texte: string, prioritaire: boolean): void;
  essayerVoix(texte: string): void;
  commencerEcoute(): void;
  terminerEcoute(): void;
  executer(commande: Commande): void;
  purger(): void;
  liberer(): void;
}

// WHY: le service Android énonce lui-même écran verrouillé, quand la WebView peut être bridée.
// Il reçoit donc les libellés déjà traduits et fait son propre remplacement de {{...}} ; c'est
// ce qui garde i18n/fr.ts seule source des mots, natif comme web.
export const CLES_PHRASES: Record<string, string> = {
  started: 'course.say.started',
  confirmFinish: 'course.say.confirmFinish',
  finishCancelled: 'course.say.finishCancelled',
  paused: 'course.say.paused',
  resumed: 'course.say.resumed',
  finished: 'course.say.finished',
  km: 'course.say.km',
  metres: 'course.say.metres',
  kilometre: 'course.say.kilometre',
  kilometres: 'course.say.kilometres',
  goalReached: 'course.say.goalReached',
  speed: 'course.say.speed',
  volumeTest: 'course.say.volumeTest',
  speedUp: 'course.say.speedUp',
  speedUpABit: 'course.say.speedUpABit',
  slowDown: 'course.say.slowDown',
  slowDownABit: 'course.say.slowDownABit',
  target: 'course.say.target',
  pace: 'course.say.pace',
  duration: 'course.say.duration',
  distance: 'course.say.distance',
  summary: 'course.say.summary',
  listening: 'course.say.listening',
  notUnderstood: 'course.say.notUnderstood',
  noPace: 'course.say.noPace',
  notification: 'course.notification',
  pause: 'course.notificationPause',
  resume: 'course.notificationResume',
  listen: 'course.notificationListen',
};

export function interpoler(modele: string, valeurs: Record<string, string | number>): string {
  let texte = modele;
  for (const [cle, valeur] of Object.entries(valeurs)) {
    texte = texte.replace(new RegExp(`\\{\\{\\s*${cle}\\s*\\}\\}`, 'g'), String(valeur));
  }
  return texte;
}
