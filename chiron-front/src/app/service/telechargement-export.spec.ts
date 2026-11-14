import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChironApi, ExportProgramme } from './chiron-api';
import { TelechargementExport, estimerTokens, nomDeFichierExport } from './telechargement-export';

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

  it('accepte une autre extension', () => {
    expect(nomDeFichierExport('Programme Force été', 'md')).toBe('Programme-Force-ete.md');
    expect(nomDeFichierExport('???', 'md')).toBe('export-chiron.md');
  });
});

describe('estimerTokens', () => {
  it('compte environ un token pour 3,5 caractères, arrondi au-dessus', () => {
    expect(estimerTokens('')).toBe(0);
    expect(estimerTokens('x'.repeat(7))).toBe(2);
    expect(estimerTokens('x'.repeat(8))).toBe(3);
  });
});

describe('TelechargementExport', () => {
  const telechargerExport = vi.fn();
  const getExportMarkdown = vi.fn();
  const writeText = vi.fn();
  let clic: ReturnType<typeof vi.spyOn>;
  let nomTelecharge: string | null;

  beforeEach(() => {
    telechargerExport.mockReset();
    getExportMarkdown.mockReset();
    writeText.mockReset().mockResolvedValue(undefined);
    vi.stubGlobal('ClipboardItem', undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    nomTelecharge = null;
    URL.createObjectURL = vi.fn().mockReturnValue('blob:test');
    URL.revokeObjectURL = vi.fn();
    clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      nomTelecharge = this.download;
    });
    TestBed.configureTestingModule({
      providers: [{ provide: ChironApi, useValue: { telechargerExport, getExportMarkdown } }],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

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

  it('télécharge la version texte sous un nom .md', async () => {
    getExportMarkdown.mockReturnValue(of('# Programme'));

    await TestBed.inject(TelechargementExport).telechargerMarkdown(classeur);

    expect(getExportMarkdown).toHaveBeenCalledWith(9);
    expect(clic).toHaveBeenCalled();
    expect(nomTelecharge).toBe('Programme-Force-ete.md');
  });

  it('propage l échec du téléchargement de la version texte', async () => {
    getExportMarkdown.mockReturnValue(throwError(() => new Error('503')));

    await expect(
      TestBed.inject(TelechargementExport).telechargerMarkdown(classeur),
    ).rejects.toThrow('503');
    expect(clic).not.toHaveBeenCalled();
  });

  it('copie la version texte et rend le nombre de tokens estimé', async () => {
    getExportMarkdown.mockReturnValue(of('x'.repeat(700)));

    const tokens = await TestBed.inject(TelechargementExport).copierPourIa(classeur);

    expect(writeText).toHaveBeenCalledWith('x'.repeat(700));
    expect(tokens).toBe(200);
  });

  it('propage un refus du presse-papiers', async () => {
    getExportMarkdown.mockReturnValue(of('texte'));
    writeText.mockRejectedValue(new DOMException('refusé', 'NotAllowedError'));

    await expect(TestBed.inject(TelechargementExport).copierPourIa(classeur)).rejects.toThrow(
      'refusé',
    );
  });
});
