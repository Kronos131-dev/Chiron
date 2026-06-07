import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from './i18n.service';

/**
 * Pipe de traduction : `{{ 'namespace.key' | t }}` ou `{{ 'k' | t:{ name: x } }}`.
 * Impur (`pure: false`) pour refléter instantanément le changement de langue à chaud.
 */
@Pipe({ name: 't', standalone: true, pure: false })
export class TranslatePipe implements PipeTransform {
  private i18n = inject(I18nService);

  transform(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }
}

/**
 * Pipe pour les champs bilingues venant de la base : `{{ ex.nomFr | localize: ex.nomEn }}`.
 * Affiche la variante de la langue courante, avec repli sur l'autre.
 */
@Pipe({ name: 'localize', standalone: true, pure: false })
export class LocalizePipe implements PipeTransform {
  private i18n = inject(I18nService);

  transform(frVal: string | null | undefined, enVal: string | null | undefined): string {
    return this.i18n.pick(frVal, enVal);
  }
}
