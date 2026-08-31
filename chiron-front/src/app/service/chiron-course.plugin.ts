import { registerPlugin } from '@capacitor/core';
import { CoursePointDto } from './chiron-api';
import { UniteAllure } from '../util/allure';

export interface ConfigurationCourse {
  cibleMinParKm: number | null;
  langue: string;
  titre: string;
  phrases: Record<string, string>;
  uniteAllure: UniteAllure;
  volumeVoix: number;
  objectifDistanceM: number;
  intervalleAnnonceM: number;
  motCle: boolean;
}

export interface EtatNatif {
  demarree: boolean;
  enPause: boolean;
  distanceM: number;
  dureeMs: number;
  allureCouranteKmh: number;
  allureMoyenneKmh: number;
  nbPoints: number;
  paliers: number;
  precisionM: number | null;
  erreurGps: string | null;
  signalPerdu: boolean;
  ecoute: boolean;
  microDisponible: boolean;
  voixPrete: boolean;
  derniereParoleA: number;
  objectifDistanceM: number;
  objectifDureeMs: number;
  cibleMinParKm: number | null;
  motCleActif: boolean;
  motCleIndisponible: string | null;
  terminee: boolean;
  nouveauxPoints?: CoursePointDto[];
}

export interface PermissionsCourse {
  localisation: boolean;
  micro: boolean;
  notifications: boolean;
  batterie: boolean;
}

export interface ChironCoursePlugin {
  disponible(): Promise<{ natif: boolean }>;
  permissions(): Promise<PermissionsCourse>;
  demanderPermissions(): Promise<PermissionsCourse>;
  demarrer(configuration: ConfigurationCourse): Promise<void>;
  configurer(configuration: Partial<ConfigurationCourse>): Promise<void>;
  configurerApiCommandes(options: { baseUrl: string; token: string }): Promise<void>;
  arreter(): Promise<EtatNatif & { points: CoursePointDto[] }>;
  basculerPause(): Promise<EtatNatif>;
  fixerCible(options: { cibleMinParKm: number | null }): Promise<EtatNatif>;
  annoncer(options: { texte: string; prioritaire: boolean }): Promise<void>;
  executerAction(options: { action: string }): Promise<EtatNatif>;
  ecouter(): Promise<void>;
  essayerVoix(options: { texte: string; langue: string; volume: number }): Promise<void>;
  etat(): Promise<EtatNatif>;
  points(): Promise<{ points: CoursePointDto[] }>;
  oublier(): Promise<void>;
  motCleDisponible(): Promise<{ disponible: boolean }>;
  exempterBatterie(): Promise<void>;
  addListener(
    evenement: 'etat',
    ecouteur: (etat: EtatNatif) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    evenement: 'commande',
    ecouteur: (donnees: { texte: string; definitif: boolean }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    evenement: 'echecEcoute',
    ecouteur: (donnees: { raison: string }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
}

export const ChironCourse = registerPlugin<ChironCoursePlugin>('ChironCourse');
