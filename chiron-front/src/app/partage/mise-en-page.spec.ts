import { describe, expect, it } from 'vitest';
import {
  STORY,
  cadreDe,
  decouperEnLignes,
  regrouperSplits,
  tailleAjustee,
  disposerPuces,
  tronquerParHauteur,
} from './mise-en-page';

const dixParCaractere = (texte: string) => texte.length * 10;

describe('mise en page', () => {
  it('garde le contenu dans le panneau, sous l entête et au-dessus du pied', () => {
    const cadre = cadreDe(STORY);
    expect(cadre.contenu.y).toBeGreaterThan(cadre.entete.y + cadre.entete.hauteur);
    expect(cadre.contenu.y + cadre.contenu.hauteur).toBeLessThan(cadre.pied.y);
    expect(cadre.pied.y + cadre.pied.hauteur).toBe(STORY.hauteur - STORY.margeVerticale);
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
    expect(tronquerParHauteur([100, 120, 90], 400, 50)).toEqual({ visibles: 3, masquees: 0 });
  });

  it('réserve la place de la ligne « autres » quand il faut tronquer', () => {
    const { visibles, masquees } = tronquerParHauteur([100, 100, 100, 100, 100], 320, 50);
    expect(visibles).toBe(2);
    expect(masquees).toBe(3);
  });

  it('garde au moins une ligne visible', () => {
    expect(tronquerParHauteur([300, 300], 100, 50).visibles).toBe(0);
    expect(tronquerParHauteur([40, 300], 100, 50).visibles).toBe(1);
  });

  it('range les puces sur plusieurs lignes', () => {
    const { lignes, masquees } = disposerPuces([100, 100, 100, 100], 250, 10, 3, () => 40);
    expect(lignes).toEqual([
      [0, 1],
      [2, 3],
    ]);
    expect(masquees).toBe(0);
  });

  it('résume les puces en trop par un « +N » qui tient sur la dernière ligne', () => {
    const { lignes, masquees } = disposerPuces(Array(8).fill(100), 250, 10, 2, () => 60);
    expect(lignes).toHaveLength(2);
    const placees = lignes.flat().length;
    expect(placees + masquees).toBe(8);
    expect(masquees).toBeGreaterThan(0);
    const derniere = lignes[1];
    expect(derniere.length * 100 + (derniere.length - 1) * 10 + 10 + 60).toBeLessThanOrEqual(250);
  });

  it('place une puce plus large que la ligne seule sur sa ligne', () => {
    expect(disposerPuces([400, 50], 300, 10, 3, () => 40).lignes).toEqual([[0], [1]]);
  });

  it('regroupe les splits au-delà du maximum en moyennes', () => {
    const valeurs = Array.from({ length: 42 }, (_, index) => index);
    const groupes = regrouperSplits(valeurs, 21);
    expect(groupes).toHaveLength(21);
    expect(groupes[0]).toBe(0.5);
    expect(regrouperSplits([1, 2, 3], 21)).toEqual([1, 2, 3]);
  });
});
