import { describe, expect, it } from 'vitest';
import {
  PORTRAIT,
  STORY,
  cadreDe,
  decouperEnLignes,
  regrouperSplits,
  tailleAjustee,
  tronquerLignes,
} from './mise-en-page';

const dixParCaractere = (texte: string) => texte.length * 10;

describe('mise en page', () => {
  it('garde le contenu dans le panneau, sous l entête et au-dessus du pied', () => {
    for (const format of [PORTRAIT, STORY]) {
      const cadre = cadreDe(format);
      expect(cadre.contenu.y).toBeGreaterThan(cadre.entete.y + cadre.entete.hauteur);
      expect(cadre.contenu.y + cadre.contenu.hauteur).toBeLessThan(cadre.pied.y);
      expect(cadre.pied.y + cadre.pied.hauteur).toBe(format.hauteur - format.margeVerticale);
    }
  });

  it('réduit la taille jusqu à tenir dans la largeur', () => {
    const mesure = (texte: string, taille: number) => texte.length * taille;
    expect(tailleAjustee('ABCDEFGHIJ', mesure, 500, 80, 20)).toBe(50);
    expect(tailleAjustee('ABCDEFGHIJ', mesure, 100, 80, 20)).toBe(20);
  });

  it('découpe en lignes et tronque la dernière avec des points de suspension', () => {
    const lignes = decouperEnLignes('PUSH PECS ET ÉPAULES DU MATIN', dixParCaractere, 120, 2);
    expect(lignes).toHaveLength(2);
    expect(lignes[0]).toBe('PUSH PECS ET');
    expect(lignes[1].endsWith('…')).toBe(true);
    expect(dixParCaractere(lignes[1])).toBeLessThanOrEqual(120);
  });

  it('ne tronque pas quand tout tient', () => {
    const { visibles, masquees } = tronquerLignes([1, 2, 3], 400, 80, 6, 50);
    expect(visibles).toEqual([1, 2, 3]);
    expect(masquees).toBe(0);
  });

  it('réserve la place de la ligne « autres » quand il faut tronquer', () => {
    const { visibles, masquees } = tronquerLignes([1, 2, 3, 4, 5, 6, 7, 8], 400, 80, 9, 50);
    expect(visibles).toHaveLength(4);
    expect(masquees).toBe(4);
  });

  it('respecte le maximum de lignes du format', () => {
    const { visibles } = tronquerLignes([1, 2, 3, 4, 5, 6, 7, 8], 2000, 80, 6, 50);
    expect(visibles).toHaveLength(6);
  });

  it('regroupe les splits au-delà du maximum en moyennes', () => {
    const valeurs = Array.from({ length: 42 }, (_, index) => index);
    const groupes = regrouperSplits(valeurs, 21);
    expect(groupes).toHaveLength(21);
    expect(groupes[0]).toBe(0.5);
    expect(regrouperSplits([1, 2, 3], 21)).toEqual([1, 2, 3]);
  });
});
