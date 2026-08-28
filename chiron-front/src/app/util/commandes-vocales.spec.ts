import { describe, expect, it } from 'vitest';
import { interpreter, lireAllure, normaliser } from './commandes-vocales';

describe('normaliser', () => {
  it('retire les accents, la ponctuation et les majuscules', () => {
    expect(normaliser('Quelle est mon ALLURE, là ?')).toBe('quelle est mon allure la');
  });
});

describe('lireAllure', () => {
  it.each([
    ['5:30', 5.5],
    ['5 30', 5.5],
    ['5 minutes 30', 5.5],
    ['cinq minutes trente', 5.5],
    ['cinq trente', 5.5],
    ['cinq et demi', 5.5],
    ['5min30', 5.5],
    ['6', 6],
    ['six', 6],
  ])('lit « %s » comme %s min/km', (texte, attendu) => {
    expect(lireAllure(texte)).toBeCloseTo(attendu, 4);
  });

  it('refuse une allure hors des bornes humaines', () => {
    expect(lireAllure('42')).toBeNull();
    expect(lireAllure('1')).toBeNull();
  });

  it('rend null sans nombre', () => {
    expect(lireAllure('vas y')).toBeNull();
  });
});

describe('interpreter', () => {
  // WHY: ce sont les formulations que l'athlète emploie réellement, essoufflé, en courant.
  // Chacune a cassé la première version, qui n'acceptait qu'un mot-clé isolé.
  it.each([
    ['quelle est mon allure', 'allure'],
    ['c est quoi mon rythme', 'allure'],
    ['allure', 'allure'],
    ['ma vitesse', 'allure'],
    ['quelle distance j ai parcouru', 'distance'],
    ['combien j ai fait', 'distance'],
    ['distance', 'distance'],
    ['depuis combien de temps je cours', 'duree'],
    ['duree', 'duree'],
    ['mon chrono', 'duree'],
    ['ou j en suis', 'bilan'],
    ['fais moi un bilan', 'bilan'],
    ['mets pause', 'pause'],
    ['arrete', 'pause'],
    ['stop', 'pause'],
    ['reprends', 'reprendre'],
    ['on y va', 'reprendre'],
    ['c est reparti', 'reprendre'],
    ['plus vite', 'plusVite'],
    ['accelere', 'plusVite'],
    ['moins vite', 'moinsVite'],
    ['ralentis', 'moinsVite'],
  ])('comprend « %s » comme %s', (phrase, attendu) => {
    expect(interpreter(phrase)?.nom).toBe(attendu);
  });

  it.each([
    ['passe a 5 minutes 30', 5.5],
    ['cible cinq minutes trente', 5.5],
    ['mets moi a 4:45', 4.75],
    ['objectif 6', 6],
    ['allure 5 30', 5.5],
  ])('règle la cible depuis « %s »', (phrase, attendu) => {
    const commande = interpreter(phrase);
    expect(commande?.nom).toBe('cible');
    expect(commande?.cibleMinParKm).toBeCloseTo(attendu, 4);
  });

  // WHY: « reprends l'allure » contient les deux vocabulaires. La commande d'action doit
  // l'emporter sur l'annonce, sinon l'athlète en pause reste en pause.
  it('préfère la reprise à l’annonce quand la phrase porte les deux', () => {
    expect(interpreter('reprends l allure')?.nom).toBe('reprendre');
  });

  it('rend null sur une phrase hors sujet', () => {
    expect(interpreter('bonjour la lune')).toBeNull();
    expect(interpreter('')).toBeNull();
  });
});
