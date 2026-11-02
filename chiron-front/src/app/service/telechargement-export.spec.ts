import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChironApi, ExportProgramme } from './chiron-api';
import { TelechargementExport, nomDeFichierExport } from './telechargement-export';

const classeur = { id: 9, nom: 'Programme Force été' } as ExportProgramme;

describe('nomDeFichierExport', () => {
  it('retire les accents et remplace les espaces par des tirets', () => {
    expect(nomDeFichierExport('Programme Force été')).toBe('Programme-Force-ete.xlsx');
  });

  it('retire les caractères interdits dans un nom de fichier', () => {
    expect(nomDeFichierExport('Push/Pull: A?')).toBe('PushPull-A.xlsx');
  });

  it('retombe sur un nom neutre quand il ne reste rien', () => {
    expect(nomDeFichierExport('???')).toBe('export-chiron.xlsx');
  });
});

describe('TelechargementExport', () => {
  const telechargerExport = vi.fn();
  let clic: ReturnType<typeof vi.spyOn>;
  let nomTelecharge: string | null;

  beforeEach(() => {
    telechargerExport.mockReset();
    nomTelecharge = null;
    URL.createObjectURL = vi.fn().mockReturnValue('blob:test');
    URL.revokeObjectURL = vi.fn();
    clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      nomTelecharge = this.download;
    });
    TestBed.configureTestingModule({
      providers: [{ provide: ChironApi, useValue: { telechargerExport } }],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  it('télécharge le classeur sous un nom de fichier propre', async () => {
    telechargerExport.mockReturnValue(of(new Blob(['x'])));

    await TestBed.inject(TelechargementExport).telecharger(classeur);

    expect(telechargerExport).toHaveBeenCalledWith(9);
    expect(clic).toHaveBeenCalled();
    expect(nomTelecharge).toBe('Programme-Force-ete.xlsx');
  });

  it('propage l échec du téléchargement', async () => {
    telechargerExport.mockReturnValue(throwError(() => new Error('503')));

    await expect(TestBed.inject(TelechargementExport).telecharger(classeur)).rejects.toThrow('503');
    expect(clic).not.toHaveBeenCalled();
  });
});
