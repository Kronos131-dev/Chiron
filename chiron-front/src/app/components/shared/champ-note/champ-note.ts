import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { TranslatePipe } from '../../../service/translate.pipe';

@Component({
  selector: 'app-champ-note',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './champ-note.html',
  styleUrl: './champ-note.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChampNote {
  readonly valeur = model<string | null | undefined>('');
  readonly libelle = input.required<string>();
  readonly indication = input.required<string>();

  saisir(evenement: Event): void {
    this.valeur.set((evenement.target as HTMLTextAreaElement).value);
  }

  effacer(): void {
    this.valeur.set('');
  }
}
