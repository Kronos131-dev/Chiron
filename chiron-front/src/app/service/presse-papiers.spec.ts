import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { copierTexte } from './presse-papiers';

class FauxClipboardItem {
  constructor(readonly elements: Record<string, Promise<Blob>>) {}
}

describe('copierTexte', () => {
  const write = vi.fn();
  const writeText = vi.fn();

  beforeEach(() => {
    write.mockReset().mockResolvedValue(undefined);
    writeText.mockReset().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { write, writeText } });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('avec ClipboardItem', () => {
    beforeEach(() => vi.stubGlobal('ClipboardItem', FauxClipboardItem));

    it('écrit tout de suite un élément dont le texte arrive plus tard', async () => {
      let livrer: (valeur: string) => void = () => undefined;
      const texte = new Promise<string>((resolve) => (livrer = resolve));

      const copie = copierTexte(texte);

      expect(write).toHaveBeenCalledTimes(1);
      livrer('# Développé couché');
      await copie;
      const item = write.mock.calls[0][0][0] as FauxClipboardItem;
      expect(await (await item.elements['text/plain']).text()).toBe('# Développé couché');
      expect(writeText).not.toHaveBeenCalled();
    });

    it('propage un refus du navigateur', async () => {
      write.mockRejectedValue(new DOMException('refusé', 'NotAllowedError'));

      await expect(copierTexte(Promise.resolve('x'))).rejects.toThrow('refusé');
    });

    it('propage l échec de la récupération du texte', async () => {
      write.mockImplementation(async ([item]: FauxClipboardItem[]) => {
        await item.elements['text/plain'];
      });

      await expect(copierTexte(Promise.reject(new Error('503')))).rejects.toThrow('503');
    });
  });

  describe('sans ClipboardItem', () => {
    beforeEach(() => vi.stubGlobal('ClipboardItem', undefined));

    it('retombe sur writeText une fois le texte reçu', async () => {
      await copierTexte(Promise.resolve('contenu'));

      expect(writeText).toHaveBeenCalledWith('contenu');
      expect(write).not.toHaveBeenCalled();
    });
  });
});
