import { Pipe, PipeTransform } from '@angular/core';
import { marked } from 'marked';

// Markdown léger et conversationnel : GFM pour les listes/gras, `breaks` pour
// qu'un simple saut de ligne devienne <br> (Chiron répond souvent en lignes courtes).
marked.setOptions({ gfm: true, breaks: true });

/**
 * Convertit le Markdown d'une réponse de Chiron en HTML.
 *
 * Le HTML produit est rendu via `[innerHTML]`, donc automatiquement assaini par le
 * sanitizer natif d'Angular (scripts / handlers / attributs dangereux retirés) :
 * aucun `bypassSecurityTrust` n'est utilisé, le rendu reste sûr par défaut.
 */
@Pipe({
  name: 'markdown',
  standalone: true,
})
export class MarkdownPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '';
    return marked.parse(value, { async: false }) as string;
  }
}
