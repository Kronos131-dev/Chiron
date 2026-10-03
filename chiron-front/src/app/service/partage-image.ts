import { Injectable } from '@angular/core';
import { CarteSeance, Traducteur } from '../partage/carte';
import { Format } from '../partage/mise-en-page';
import { IssuePartage, partagerImage } from './partage';

@Injectable({ providedIn: 'root' })
export class PartageImage {
  async forger(carte: CarteSeance, format: Format, t: Traducteur, langue: string): Promise<Blob> {
    const { rendreCarte } = await import('../partage/rendu');
    return rendreCarte(carte, format, t, langue);
  }

  partager(fichier: File, texte: string): Promise<IssuePartage> {
    return partagerImage(fichier, texte);
  }
}
