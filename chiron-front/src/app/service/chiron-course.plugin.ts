import { registerPlugin } from '@capacitor/core';
import { CoursePointDto } from './chiron-api';
import { MappingCasque } from '../util/telecommande-casque';
import { UniteAllure } from '../util/allure';

export interface ConfigurationCourse {
  cibleMinParKm: number | null;
  langue: string;
  titre: string;
  phrases: Record<string, string>;
  appuiCourt: MappingCasque;
  appuiLong: MappingCasque;
  uniteAllure: UniteAllure;
  volumeVoix: number;
}

export interface EtatNatif {
  demarree: boolean;
  enPause: boolean;
  distanceM: number;
  dureeMs: number;
  allureCouranteKmh: number;
  allureMoyenneKmh: number;
  nbPoints: number;
  kilometres: number;
  precisionM: number | null;
  erreurGps: string | null;
  signalPerdu: boolean;
  ecoute: boolean;
  microDisponible: boolean;
  voixPrete: boolean;
  derniereParoleA: number;
  cibleMinParKm: number | null;
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
  arreter(): Promise<EtatNatif & { points: CoursePointDto[] }>;
  basculerPause(): Promise<EtatNatif>;
  fixerCible(options: { cibleMinParKm: number | null }): Promise<EtatNatif>;
  annoncer(options: { texte: string; prioritaire: boolean }): Promise<void>;
  executerAction(options: { action: string }): Promise<EtatNatif>;
  ecouter(): Promise<void>;
  essayerVoix(options: { texte: string; langue: string; volume: number }): Promise<void>;
  etat(): Promise<EtatNatif>;
  points(): Promise<{ points: CoursePointDto[] }>;
  exempterBatterie(): Promise<void>;
  addListener(
    evenement: 'etat',
    ecouteur: (etat: EtatNatif) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    evenement: 'kilometre',
    ecouteur: (donnees: { kilometre: number; dureeSplitS: number; allureSplitKmh: number }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    evenement: 'commande',
    ecouteur: (donnees: { texte: string; definitif: boolean }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    evenement: 'casque',
    ecouteur: (donnees: { action: string }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    evenement: 'echecEcoute',
    ecouteur: (donnees: { raison: string }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
}

export const ChironCourse = registerPlugin<ChironCoursePlugin>('ChironCourse');
