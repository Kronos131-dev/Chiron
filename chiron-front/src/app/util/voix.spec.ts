import { describe, expect, it } from 'vitest';
import { choisirVoixGrave, estMasculine, noterVoix } from './voix';

function voix(name: string, lang: string, localService = true): SpeechSynthesisVoice {
  return { name, lang, voiceURI: name, default: false, localService } as SpeechSynthesisVoice;
}

describe('choisirVoixGrave', () => {
  it('préfère une voix masculine connue dans la langue demandée', () => {
    const catalogue = [voix('Amélie', 'fr-FR'), voix('Thomas', 'fr-FR'), voix('Daniel', 'en-GB')];
    expect(choisirVoixGrave(catalogue, 'fr-FR')?.name).toBe('Thomas');
  });

  it('reconnaît une voix qui se déclare masculine sans nom connu', () => {
    const catalogue = [voix('French Female 2', 'fr-FR'), voix('French Male 1', 'fr-FR')];
    expect(choisirVoixGrave(catalogue, 'fr-FR')?.name).toBe('French Male 1');
  });

  // WHY: Chiron parle français. Une voix anglaise, même masculine, écorcherait « kilomètre ».
  it('ne sort pas de la langue demandée quand elle est représentée', () => {
    const catalogue = [voix('Daniel', 'en-GB'), voix('Voix française', 'fr-FR')];
    expect(choisirVoixGrave(catalogue, 'fr-FR')?.lang).toBe('fr-FR');
  });

  it('évite une voix féminine à défaut de masculine identifiée', () => {
    const catalogue = [voix('Audrey', 'fr-FR'), voix('Voix neutre', 'fr-FR')];
    expect(choisirVoixGrave(catalogue, 'fr-FR')?.name).toBe('Voix neutre');
  });

  it('rend null sur un catalogue vide', () => {
    expect(choisirVoixGrave([], 'fr-FR')).toBeNull();
  });

  // WHY: les moteurs embarqués « compact » sonnent comme un répondeur. Une voix neutre de
  // haute qualité vaut mieux qu'une voix masculine compressée, qu'on rattrape à la hauteur.
  it('préfère la haute qualité à un masculin compressé', () => {
    const catalogue = [
      voix('French Male Compact', 'fr-FR'),
      voix('Google français', 'fr-FR', false),
    ];
    expect(choisirVoixGrave(catalogue, 'fr-FR')?.name).toBe('Google français');
  });

  it('préfère la langue exacte à une variante régionale', () => {
    const catalogue = [voix('Thomas', 'fr-CA'), voix('Nicolas', 'fr-FR')];
    expect(choisirVoixGrave(catalogue, 'fr-FR')?.name).toBe('Nicolas');
  });

  it('note une voix réseau au-dessus de la même voix locale', () => {
    const reseau = voix('Google français', 'fr-FR', false);
    const locale = voix('Google français', 'fr-FR', true);
    expect(noterVoix(reseau, 'fr-FR')).toBeGreaterThan(noterVoix(locale, 'fr-FR'));
  });
});

describe('estMasculine', () => {
  it('reconnaît un prénom masculin et refuse une voix féminine', () => {
    expect(estMasculine(voix('Thomas', 'fr-FR'))).toBe(true);
    expect(estMasculine(voix('French Female 2', 'fr-FR'))).toBe(false);
    expect(estMasculine(null)).toBe(false);
  });
});
