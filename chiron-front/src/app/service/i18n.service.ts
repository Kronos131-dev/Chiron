import { Injectable, signal } from '@angular/core';
import { fr } from '../i18n/fr';
import { en } from '../i18n/en';

export type Lang = 'fr' | 'en';

const STORAGE_KEY = 'chiron_lang';

/**
 * Service d'internationalisation maison, basé Signals (aucune dépendance externe).
 * La langue courante est un signal persisté en localStorage ; le changement est instantané
 * pour tout le texte rendu via le pipe `| t` (impur) ou via {@link t}.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {

  private readonly dicts: Record<Lang, Record<string, string>> = { fr, en };

  /** Langue active de l'application ('fr' | 'en'). */
  readonly lang = signal<Lang>(this.initialLang());

  constructor() {
    document.documentElement.lang = this.lang();
  }

  private initialLang(): Lang {
    const stored = (typeof localStorage !== 'undefined') ? localStorage.getItem(STORAGE_KEY) : null;
    if (stored === 'fr' || stored === 'en') {
      return stored;
    }
    const nav = (typeof navigator !== 'undefined' ? navigator.language : 'fr') || 'fr';
    return nav.toLowerCase().startsWith('en') ? 'en' : 'fr';
  }

  /** Change la langue, la persiste et met à jour l'attribut lang du document. */
  setLang(l: Lang): void {
    this.lang.set(l);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, l);
    }
    document.documentElement.lang = l;
  }

  /**
   * Traduit une clé dans la langue courante. Repli sur le français puis sur la clé brute si
   * absente. Interpole les paramètres `{{nom}}`.
   */
  t(key: string, params?: Record<string, string | number>): string {
    let value = this.dicts[this.lang()][key] ?? this.dicts.fr[key] ?? key;
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        value = value.replace(new RegExp(`\\{\\{\\s*${k}\\s*\\}\\}`, 'g'), String(v));
      }
    }
    return value;
  }

  /** Choisit la variante linguistique d'un champ bilingue venant de la base (ex. nomFr/nomEn). */
  pick(frVal: string | null | undefined, enVal: string | null | undefined): string {
    return this.lang() === 'en' ? (enVal ?? frVal ?? '') : (frVal ?? enVal ?? '');
  }
}
