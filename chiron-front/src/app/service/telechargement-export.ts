import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ChironApi, ExportProgramme } from './chiron-api';
import { partagerFichier } from './partage';

const TYPE_XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

export function nomDeFichierExport(nom: string): string {
  const propre = nom
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[^A-Za-z0-9 _-]/g, '')
    .trim()
    .replace(/\s+/g, '-');
  return `${propre || 'export-chiron'}.xlsx`;
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
}
