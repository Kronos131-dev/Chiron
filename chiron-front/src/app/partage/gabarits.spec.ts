import { describe, expect, it } from 'vitest';
import { fr } from '../i18n/fr';
import { carteBatailleExemple, carteCourseExemple, carteWodExemple } from './exemples';
import { Traducteur } from './carte';
import { STORY } from './mise-en-page';
import { palierDe } from './paliers';
import { Scene } from './pinceaux/scene';
import { dessinerCarte } from './rendu';

const traducteurFr: Traducteur = (cle, params) =>
  (fr[cle] ?? cle).replace(/\{\{\s*(\w+)\s*\}\}/g, (_, nom: string) => String(params?.[nom] ?? ''));

interface Appel {
  nom: string;
  args: unknown[];
}

function contexteEnregistreur() {
  const appels: Appel[] = [];
  const valeurs: Record<string, unknown> = {
    measureText: (texte: string) => ({ width: texte.length * 12 }),
    createLinearGradient: () => ({ addColorStop: () => undefined }),
    createRadialGradient: () => ({ addColorStop: () => undefined }),
  };
  const ctx = new Proxy(valeurs, {
    get(cible, propriete: string) {
      if (propriete in cible) return cible[propriete];
      return (...args: unknown[]) => {
        appels.push({ nom: propriete, args });
      };
    },
    set(cible, propriete: string, valeur) {
      cible[propriete] = valeur;
      return true;
    },
  }) as unknown as CanvasRenderingContext2D;
  return { ctx, appels };
}

function dessiner(carte: ReturnType<typeof carteBatailleExemple>, niveau: number) {
  const { ctx, appels } = contexteEnregistreur();
  const scene: Scene = {
    ctx,
    format: STORY,
    palier: palierDe(niveau),
    carte,
    res: { fond: null, badge: null, icone: null },
    t: traducteurFr,
    langue: 'fr',
  };
  dessinerCarte(scene);
  const textes = appels
    .filter((appel) => appel.nom === 'fillText')
    .map((appel) => String(appel.args[0]));
  return { appels, textes };
}

describe('gabarits', () => {
  it('dessine la bataille avec son titre, ses tuiles et toutes les séries', () => {
    const carte = carteBatailleExemple(5, traducteurFr, 'fr');
    const { textes } = dessiner(carte, 5);
    const tout = textes.join('');
    expect(textes).toContain('Développé couché');
    expect(textes).toContain('100 × 8');
    expect(textes).toContain('95 × 10');
    expect(textes).toContain('90 × 12 › 70 × 8');
    expect(tout).toContain('PUSH');
    expect(tout).toContain('Exercices'.toUpperCase());
    expect(tout).not.toMatch(/tonnage/i);
  });

  it('renvoie vers les exercices qui ne tiennent pas', () => {
    const exercices = carteBatailleExemple(5, traducteurFr, 'fr');
    const beaucoup = {
      ...exercices,
      bataille: {
        ...exercices.bataille!,
        lignes: Array.from({ length: 30 }, (_, index) => ({
          ...exercices.bataille!.lignes[0],
          nom: `Exercice ${index + 1}`,
        })),
      },
    };
    const { textes } = dessiner(beaucoup, 5);
    expect(textes.join('')).toContain('autres');
    expect(textes.filter((t) => t.startsWith('Exercice ')).length).toBeLessThan(30);
  });

  it('dessine la course avec la distance, le sceau et le tracé', () => {
    const carte = carteCourseExemple(8, traducteurFr, 'fr');
    const { textes, appels } = dessiner(carte, 8);
    expect(textes.some((t) => t.startsWith('10,'))).toBe(true);
    expect(textes.join('')).toContain('KM');
    expect(textes.join('')).toContain('OBJECTIF');
    expect(appels.filter((appel) => appel.nom === 'stroke').length).toBeGreaterThan(10);
  });

  it('dessine le WOD avec les tours et le bandeau de record', () => {
    const carte = carteWodExemple(8, traducteurFr);
    const { textes } = dessiner(carte, 8);
    expect(textes).toContain('18');
    expect(textes.join('')).toContain('NOUVEAU RECORD');
    expect(textes.join('')).toContain('540');
    expect(textes).toEqual(expect.arrayContaining(['90', '180', '270']));
  });

  it('dessine aussi sans palier', () => {
    const carte = carteBatailleExemple(null, traducteurFr, 'fr');
    expect(() => dessiner(carte, 0)).not.toThrow();
  });

  it('couvre les huit paliers sans erreur', () => {
    for (let niveau = 1; niveau <= 8; niveau++) {
      expect(() => dessiner(carteWodExemple(niveau, traducteurFr), niveau)).not.toThrow();
    }
    expect(Object.keys(fr).some((cle) => cle.startsWith('share.'))).toBe(true);
  });
});
