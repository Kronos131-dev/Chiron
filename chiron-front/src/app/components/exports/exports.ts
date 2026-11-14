import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ChironApi, ExportProgramme } from '../../service/chiron-api';
import { I18nService } from '../../service/i18n.service';
import { TelechargementExport } from '../../service/telechargement-export';
import { TranslatePipe } from '../../service/translate.pipe';
import { HeaderComponent } from '../shared/header/header';

const DUREE_MESSAGE_COPIE_MS = 3000;

export interface CopieReussie {
  id: number;
  tokens: string;
}

@Component({
  selector: 'app-exports',
  standalone: true,
  imports: [HeaderComponent, TranslatePipe],
  templateUrl: './exports.html',
  styleUrl: './exports.css',
})
export class Exports implements OnInit, OnDestroy {
  readonly exports = signal<ExportProgramme[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly occupe = signal<number | null>(null);
  readonly copie = signal<CopieReussie | null>(null);

  private minuteurCopie: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private chironApi: ChironApi,
    private router: Router,
    private i18n: I18nService,
    private telechargementExport: TelechargementExport,
  ) {}

  ngOnInit(): void {
    this.chironApi.getExports().subscribe({
      next: (liste) => {
        this.exports.set(liste);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set(this.i18n.t('export.list.loadError'));
      },
    });
  }

  nouvelExport(): void {
    this.router.navigate(['/exports/new']);
  }

  modifier(classeur: ExportProgramme): void {
    this.router.navigate(['/exports', classeur.id, 'edit']);
  }

  async telecharger(classeur: ExportProgramme): Promise<void> {
    await this.enOccupation(classeur, 'export.list.downloadError', () =>
      this.telechargementExport.telecharger(classeur),
    );
  }

  async telechargerMarkdown(classeur: ExportProgramme): Promise<void> {
    await this.enOccupation(classeur, 'export.list.downloadError', () =>
      this.telechargementExport.telechargerMarkdown(classeur),
    );
  }

  async copierPourIa(classeur: ExportProgramme): Promise<void> {
    await this.enOccupation(classeur, 'export.list.copyError', async () => {
      const tokens = await this.telechargementExport.copierPourIa(classeur);
      this.signalerCopie(classeur.id, tokens);
    });
  }

  supprimer(classeur: ExportProgramme): void {
    if (this.occupe() !== null) return;
    if (!confirm(this.i18n.t('export.list.deleteConfirm'))) return;
    this.erreur.set(null);
    this.occupe.set(classeur.id);
    this.chironApi.supprimerExport(classeur.id).subscribe({
      next: () => {
        this.exports.update((liste) => liste.filter((e) => e.id !== classeur.id));
        this.occupe.set(null);
      },
      error: () => {
        this.occupe.set(null);
        this.erreur.set(this.i18n.t('export.list.deleteError'));
      },
    });
  }

  ngOnDestroy(): void {
    if (this.minuteurCopie !== null) clearTimeout(this.minuteurCopie);
  }

  formaterDate(valeur: string): string {
    return new Date(valeur).toLocaleDateString(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }

  private async enOccupation(
    classeur: ExportProgramme,
    cleErreur: string,
    travail: () => Promise<void>,
  ): Promise<void> {
    if (this.occupe() !== null) return;
    this.erreur.set(null);
    this.occupe.set(classeur.id);
    try {
      await travail();
    } catch {
      this.erreur.set(this.i18n.t(cleErreur));
    } finally {
      this.occupe.set(null);
    }
  }

  private signalerCopie(id: number, tokens: number): void {
    const arrondi = Number(tokens.toPrecision(2));
    this.copie.set({
      id,
      tokens: arrondi.toLocaleString(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR'),
    });
    if (this.minuteurCopie !== null) clearTimeout(this.minuteurCopie);
    this.minuteurCopie = setTimeout(() => this.copie.set(null), DUREE_MESSAGE_COPIE_MS);
  }
}
