import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { partagerImage } from './partage';

const fichier = new File(['x'], 'chiron-portrait.jpg', { type: 'image/jpeg' });

describe('partagerImage (web)', () => {
  const share = vi.fn();
  const canShare = vi.fn();

  beforeEach(() => {
    share.mockReset();
    canShare.mockReset();
    Object.assign(navigator, {
      share,
      canShare,
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
    URL.createObjectURL = vi.fn().mockReturnValue('blob:test');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ouvre la feuille de partage du système quand les fichiers sont acceptés', async () => {
    canShare.mockReturnValue(true);
    share.mockResolvedValue(undefined);
    expect(await partagerImage(fichier, 'texte')).toBe('partage');
    expect(share).toHaveBeenCalledWith({ files: [fichier], text: 'texte' });
  });

  it('signale une annulation sans télécharger', async () => {
    canShare.mockReturnValue(true);
    share.mockRejectedValue(new DOMException('annulé', 'AbortError'));
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    expect(await partagerImage(fichier, 'texte')).toBe('annule');
    expect(clic).not.toHaveBeenCalled();
  });

  it('télécharge l image quand le partage de fichiers n existe pas', async () => {
    canShare.mockReturnValue(false);
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    expect(await partagerImage(fichier, 'texte')).toBe('telechargement');
    expect(share).not.toHaveBeenCalled();
    expect(clic).toHaveBeenCalled();
  });
});
