import { Injectable } from '@angular/core';
import { CarteSeance, Traducteur } from '../partage/carte';
import { IssuePartage, partagerFichier } from './partage';

@Injectable({ providedIn: 'root' })
export class PartageImage {
  async forger(carte: CarteSeance, t: Traducteur, langue: string): Promise<Blob> {
    const { rendreCarte } = await import('../partage/rendu');
    return rendreCarte(carte, t, langue);
  }

  partager(fichier: File): Promise<IssuePartage> {
    return partagerFichier(fichier);
  }
}
