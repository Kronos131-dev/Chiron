import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { carteWodExemple } from '../../../partage/exemples';
import { I18nService } from '../../../service/i18n.service';
import { PartageImage } from '../../../service/partage-image';
import { PartageCarte } from './partage-carte';

const forger = vi.fn();
const partager = vi.fn();

describe('PartageCarte', () => {
  let fixture: ComponentFixture<PartageCarte>;
  let composant: PartageCarte;

  async function ouvrir() {
    fixture = TestBed.createComponent(PartageCarte);
    composant = fixture.componentInstance;
    const i18n = TestBed.inject(I18nService);
    composant.carte = carteWodExemple(5, (cle, params) => i18n.t(cle, params));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    forger.mockReset();
    partager.mockReset();
    forger.mockResolvedValue(new Blob(['jpg'], { type: 'image/jpeg' }));
    URL.createObjectURL = vi.fn().mockReturnValue('blob:apercu');
    URL.revokeObjectURL = vi.fn();
    TestBed.configureTestingModule({
      imports: [PartageCarte],
      providers: [{ provide: PartageImage, useValue: { forger, partager } }],
    });
  });

  it('forge la carte dès l ouverture et affiche l aperçu', async () => {
    await ouvrir();
    expect(forger).toHaveBeenCalledTimes(1);
    expect(composant.etat()).toBe('pret');
    expect(fixture.nativeElement.querySelector('img')?.getAttribute('src')).toBe('blob:apercu');
  });

  it('libère l aperçu à la fermeture', async () => {
    await ouvrir();
    fixture.destroy();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:apercu');
  });

  it('affiche une erreur quand le rendu échoue', async () => {
    forger.mockRejectedValue(new Error('canvas'));
    await ouvrir();
    expect(composant.etat()).toBe('erreur');
  });

  it('transmet le fichier forgé au service de partage', async () => {
    partager.mockResolvedValue('partage');
    await ouvrir();
    await composant.partager();
    expect(partager).toHaveBeenCalledTimes(1);
    expect(partager.mock.calls[0]).toHaveLength(1);
    expect(partager.mock.calls[0][0].name).toBe('chiron-seance.jpg');
  });

  it('prévient quand l image a été enregistrée au lieu d être partagée', async () => {
    partager.mockResolvedValue('telechargement');
    await ouvrir();
    await composant.partager();
    expect(composant.message()).toBe(TestBed.inject(I18nService).t('share.downloaded'));
  });

  it('émet la fermeture', async () => {
    await ouvrir();
    const fermer = vi.fn();
    composant.fermer.subscribe(fermer);
    fixture.nativeElement.querySelector('button').click();
    expect(fermer).toHaveBeenCalled();
  });
});
