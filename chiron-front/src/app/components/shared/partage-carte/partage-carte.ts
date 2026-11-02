import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { I18nService } from '../../../service/i18n.service';
import { PartageImage } from '../../../service/partage-image';
import { TranslatePipe } from '../../../service/translate.pipe';
import { CarteSeance } from '../../../partage/carte';

type Etat = 'forge' | 'pret' | 'erreur';

@Component({
  selector: 'app-partage-carte',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './partage-carte.html',
  styleUrl: './partage-carte.css',
})
export class PartageCarte implements OnInit, OnDestroy {
  @Input({ required: true }) carte!: CarteSeance;
  @Output() fermer = new EventEmitter<void>();

  readonly etat = signal<Etat>('forge');
  readonly apercu = signal<string | null>(null);
  readonly message = signal<string | null>(null);

  private fichier: File | null = null;

  constructor(
    private i18n: I18nService,
    private partageImage: PartageImage,
  ) {}

  ngOnInit(): void {
    this.forger();
  }

  ngOnDestroy(): void {
    const adresse = this.apercu();
    if (adresse) URL.revokeObjectURL(adresse);
  }

  async partager(): Promise<void> {
    if (!this.fichier) return;
    const issue = await this.partageImage.partager(this.fichier).catch(() => null);
    if (issue === 'telechargement') this.message.set(this.i18n.t('share.downloaded'));
    if (issue === null) this.message.set(this.i18n.t('share.shareError'));
  }

  private async forger(): Promise<void> {
    try {
      const blob = await this.partageImage.forger(
        this.carte,
        (cle, params) => this.i18n.t(cle, params),
        this.i18n.lang(),
      );
      this.fichier = new File([blob], 'chiron-seance.jpg', { type: 'image/jpeg' });
      this.apercu.set(URL.createObjectURL(blob));
      this.etat.set('pret');
    } catch {
      this.etat.set('erreur');
    }
  }
}
