import { describe, expect, it } from 'vitest';
import { fr } from '../i18n/fr';
import { carteBatailleExemple, carteCourseExemple, carteWodExemple } from './exemples';
import { Traducteur } from './carte';
import { PORTRAIT, STORY, Format } from './mise-en-page';
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

function dessiner(carte: ReturnType<typeof carteBatailleExemple>, format: Format, niveau: number) {
  const { ctx, appels } = contexteEnregistreur();
  const scene: Scene = {
    ctx,
    format,
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
  it('dessine la bataille avec son titre, son bilan et un renvoi vers les exercices masqués', () => {
    const carte = carteBatailleExemple(5, traducteurFr, 'fr');
    const { textes } = dessiner(carte, PORTRAIT, 5);
    expect(textes).toContain('Développé couché');
    expect(textes).toContain('100 kg × 8');
    expect(textes.join('')).toContain('PUSH');
    expect(textes.join('')).toContain('autres');
  });

  it('montre plus de lignes en story qu en portrait', () => {
    const carte = carteBatailleExemple(3, traducteurFr, 'fr');
    const portrait = dessiner(carte, PORTRAIT, 3).textes.filter((t) => /× \d+$/.test(t));
    const story = dessiner(carte, STORY, 3).textes.filter((t) => /× \d+$/.test(t));
    expect(story.length).toBeGreaterThan(portrait.length);
  });

  it('dessine la course avec la distance, le sceau et le tracé', () => {
    const carte = carteCourseExemple(8, traducteurFr, 'fr');
    const { textes, appels } = dessiner(carte, PORTRAIT, 8);
    expect(textes.some((t) => t.startsWith('10,'))).toBe(true);
    expect(textes.join('')).toContain('KM');
    expect(textes.join('')).toContain('OBJECTIF');
    expect(appels.filter((appel) => appel.nom === 'stroke').length).toBeGreaterThan(10);
  });

  it('dessine le WOD avec les tours et le bandeau de record', () => {
    const carte = carteWodExemple(8, traducteurFr);
    const { textes } = dessiner(carte, PORTRAIT, 8);
    expect(textes).toContain('18');
    expect(textes.join('')).toContain('NOUVEAU RECORD');
    expect(textes.join('')).toContain('540');
    expect(textes).toEqual(expect.arrayContaining(['90', '180', '270']));
  });

  it('dessine aussi sans palier', () => {
    const carte = carteBatailleExemple(null, traducteurFr, 'fr');
    expect(() => dessiner(carte, STORY, 0)).not.toThrow();
  });

  it('couvre les huit paliers sans erreur', () => {
    for (let niveau = 1; niveau <= 8; niveau++) {
      expect(() => dessiner(carteWodExemple(niveau, traducteurFr), PORTRAIT, niveau)).not.toThrow();
    }
    expect(Object.keys(fr).some((cle) => cle.startsWith('share.'))).toBe(true);
  });
});
