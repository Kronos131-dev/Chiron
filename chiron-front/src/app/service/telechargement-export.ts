import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ChironApi, ExportProgramme } from './chiron-api';
import { partagerFichier } from './partage';
import { copierTexte } from './presse-papiers';

const TYPE_XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const TYPE_MARKDOWN = 'text/markdown';
const CARACTERES_PAR_TOKEN = 3.5;

export function nomDeFichierExport(nom: string, extension = 'xlsx'): string {
  const propre = nom
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[^A-Za-z0-9 _-]/g, '')
    .trim()
    .replace(/\s+/g, '-');
  return `${propre || 'export-chiron'}.${extension}`;
}

export function estimerTokens(texte: string): number {
  return Math.ceil(texte.length / CARACTERES_PAR_TOKEN);
}

@Injectable({ providedIn: 'root' })
export class TelechargementExport {
  constructor(private chironApi: ChironApi) {}

  async telecharger(classeur: ExportProgramme): Promise<void> {
    const blob = await firstValueFrom(this.chironApi.telechargerExport(classeur.id));
    const fichier = new File([blob], nomDeFichierExport(classeur.nom), {
      type: blob.type || TYPE_XLSX,
    });
    await partagerFichier(fichier, { telecharger: true });
  }

  async telechargerMarkdown(classeur: ExportProgramme): Promise<void> {
    const texte = await firstValueFrom(this.chironApi.getExportMarkdown(classeur.id));
    const fichier = new File([texte], nomDeFichierExport(classeur.nom, 'md'), {
      type: TYPE_MARKDOWN,
    });
    await partagerFichier(fichier, { telecharger: true });
  }

  async copierPourIa(classeur: ExportProgramme): Promise<number> {
    const texte = firstValueFrom(this.chironApi.getExportMarkdown(classeur.id));
    await copierTexte(texte);
    return estimerTokens(await texte);
  }
}
