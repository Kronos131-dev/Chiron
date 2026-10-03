import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { HeaderComponent } from '../shared/header/header';
import { GenreCarte } from '../../partage/carte';
import { carteBatailleExemple, carteCourseExemple, carteWodExemple } from '../../partage/exemples';
import { FORMATS, NomFormat } from '../../partage/mise-en-page';

@Component({
  selector: 'app-labo-carte',
  standalone: true,
  imports: [HeaderComponent, TranslatePipe],
  templateUrl: './labo-carte.html',
  styleUrl: './labo-carte.css',
})
export class LaboCarte implements OnInit, OnDestroy {
  readonly genres: GenreCarte[] = ['bataille', 'course', 'wod'];
  readonly formats: NomFormat[] = ['portrait', 'story'];
  readonly niveaux = [0, 1, 2, 3, 4, 5, 6, 7, 8];

  readonly genre = signal<GenreCarte>('bataille');
  readonly format = signal<NomFormat>('portrait');
  readonly niveau = signal(5);
  readonly apercu = signal<string | null>(null);
  readonly enCours = signal(false);

  private ordre = 0;

  constructor(private i18n: I18nService) {}

  ngOnInit(): void {
    this.forger();
  }

  ngOnDestroy(): void {
    this.oublier();
  }

  choisirGenre(genre: GenreCarte): void {
    this.genre.set(genre);
    this.forger();
  }

  choisirFormat(format: NomFormat): void {
    this.format.set(format);
    this.forger();
  }

  choisirNiveau(niveau: number): void {
    this.niveau.set(niveau);
    this.forger();
  }

  private async forger(): Promise<void> {
    const ordre = ++this.ordre;
    this.enCours.set(true);
    const t = (cle: string, params?: Record<string, string | number>) => this.i18n.t(cle, params);
    const langue = this.i18n.lang();
    const niveau = this.niveau() || null;
    const carte =
      this.genre() === 'course'
        ? carteCourseExemple(niveau, t, langue)
        : this.genre() === 'wod'
          ? carteWodExemple(niveau, t)
          : carteBatailleExemple(niveau, t, langue);
    const { rendreCarte } = await import('../../partage/rendu');
    const blob = await rendreCarte(carte, FORMATS[this.format()], t, langue).catch(() => null);
    if (ordre !== this.ordre) return;
    this.oublier();
    if (blob) this.apercu.set(URL.createObjectURL(blob));
    this.enCours.set(false);
  }

  private oublier(): void {
    const adresse = this.apercu();
    if (adresse) URL.revokeObjectURL(adresse);
    this.apercu.set(null);
  }
}
