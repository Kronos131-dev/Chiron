import { describe, expect, it } from 'vitest';
import { formaterAllureSelon, lireCible, minParKmVersKmh } from './allure';

describe('allure', () => {
  describe('formaterAllureSelon', () => {
    it('rend des minutes par kilomètre', () => {
      expect(formaterAllureSelon(12, 'minParKm')).toBe('5:00');
      expect(formaterAllureSelon(10, 'minParKm')).toBe('6:00');
    });

    it('rend une vitesse à la décimale', () => {
      expect(formaterAllureSelon(12, 'kmh')).toBe('12.0');
      expect(formaterAllureSelon(9.44, 'kmh')).toBe('9.4');
    });

    it('rend un tiret pour une allure inexistante', () => {
      expect(formaterAllureSelon(0, 'minParKm')).toBe('—');
      expect(formaterAllureSelon(0, 'kmh')).toBe('—');
    });
  });

  describe('lireCible', () => {
    it('lit les trois écritures de la minute par kilomètre', () => {
      expect(lireCible('5:30', 'minParKm')).toBeCloseTo(5.5, 4);
      expect(lireCible('5 30', 'minParKm')).toBeCloseTo(5.5, 4);
      expect(lireCible('5 minutes 30', 'minParKm')).toBeCloseTo(5.5, 4);
    });

    it('lit une vitesse écrite avec un point ou une virgule', () => {
      expect(lireCible('12', 'kmh')).toBeCloseTo(5, 4);
      expect(lireCible('11,5', 'kmh')).toBeCloseTo(60 / 11.5, 4);
      expect(lireCible('11.5', 'kmh')).toBeCloseTo(60 / 11.5, 4);
    });

    it('refuse une saisie illisible', () => {
      expect(lireCible('nawak', 'kmh')).toBeNull();
      expect(lireCible('nawak', 'minParKm')).toBeNull();
      expect(lireCible('0', 'kmh')).toBeNull();
    });

    it('fait l’aller-retour entre les deux unités', () => {
      const cible = lireCible('12', 'kmh')!;
      expect(minParKmVersKmh(cible)).toBeCloseTo(12, 4);
    });
  });
});
