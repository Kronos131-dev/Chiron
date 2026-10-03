import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { I18nService } from '../../../service/i18n.service';
import { PartageImage } from '../../../service/partage-image';
import { TranslatePipe } from '../../../service/translate.pipe';
import { CarteSeance, texteAccompagnement } from '../../../partage/carte';
import { FORMATS, NomFormat } from '../../../partage/mise-en-page';

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

  readonly format = signal<NomFormat>('portrait');
  readonly etat = signal<Etat>('forge');
  readonly apercu = signal<string | null>(null);
  readonly message = signal<string | null>(null);

  protected readonly formats: NomFormat[] = ['portrait', 'story'];

  private fichier: File | null = null;
  private forgeEnCours = 0;

  constructor(
    private i18n: I18nService,
    private partageImage: PartageImage,
  ) {}

  ngOnInit(): void {
    this.forger();
  }

  ngOnDestroy(): void {
    this.oublierApercu();
  }

  changerFormat(nom: NomFormat): void {
    if (nom === this.format()) return;
    this.format.set(nom);
    this.forger();
  }

  async partager(): Promise<void> {
    if (!this.fichier) return;
    const texte = texteAccompagnement(this.carte, (cle, params) => this.i18n.t(cle, params));
    const issue = await this.partageImage.partager(this.fichier, texte).catch(() => null);
    if (issue === 'telechargement') this.message.set(this.i18n.t('share.downloaded'));
    if (issue === null) this.message.set(this.i18n.t('share.shareError'));
  }

  private async forger(): Promise<void> {
    const ordre = ++this.forgeEnCours;
    this.etat.set('forge');
    this.message.set(null);
    this.fichier = null;
    try {
      const blob = await this.partageImage.forger(
        this.carte,
        FORMATS[this.format()],
        (cle, params) => this.i18n.t(cle, params),
        this.i18n.lang(),
      );
      if (ordre !== this.forgeEnCours) return;
      this.oublierApercu();
      this.fichier = new File([blob], `chiron-${this.format()}.jpg`, { type: 'image/jpeg' });
      this.apercu.set(URL.createObjectURL(blob));
      this.etat.set('pret');
    } catch {
      if (ordre === this.forgeEnCours) this.etat.set('erreur');
    }
  }

  private oublierApercu(): void {
    const adresse = this.apercu();
    if (adresse) URL.revokeObjectURL(adresse);
    this.apercu.set(null);
  }
}
