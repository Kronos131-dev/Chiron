import { Component, OnInit, computed, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { AuthService } from '../../service/auth.service';
import {
  ChironApi,
  ExportProgramme,
  ExportProgrammeRequete,
  SeanceExport,
} from '../../service/chiron-api';
import { I18nService } from '../../service/i18n.service';
import { TelechargementExport } from '../../service/telechargement-export';
import { TranslatePipe } from '../../service/translate.pipe';
import {
  SeanceFaite,
  SeanceJournalBrute,
  grouperParSemaine,
  modeleAutomatique,
  modelesParTitre,
  seancesTerminees,
} from '../../util/seances-export';
import { HeaderComponent } from '../shared/header/header';

export interface RoutineChoix {
  id: number;
  titre: string;
  nbExercices: number;
  nbSeries: number;
}

export type EtapeAssistant = 1 | 2 | 3;

const DERNIERE_ETAPE: EtapeAssistant = 3;

@Component({
  selector: 'app-export-assistant',
  standalone: true,
  imports: [HeaderComponent, TranslatePipe],
  templateUrl: './export-assistant.html',
  styleUrl: './export-assistant.css',
})
export class ExportAssistant implements OnInit {
  readonly etape = signal<EtapeAssistant>(1);
  readonly chargement = signal(true);
  readonly erreurChargement = signal(false);
  readonly routines = signal<RoutineChoix[]>([]);
  readonly modelesChoisis = signal<ReadonlySet<number>>(new Set());
  readonly journal = signal<SeanceFaite[]>([]);
  readonly ajoutees = signal<ReadonlyMap<number, number | null>>(new Map());
  readonly nom = signal('');
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly resultat = signal<ExportProgramme | null>(null);
  readonly telechargementEnCours = signal(false);

  readonly exportId = signal<number | null>(null);
  readonly enEdition = computed(() => this.exportId() !== null);

  readonly routinesChoisies = computed(() =>
    this.routines().filter((routine) => this.modelesChoisis().has(routine.id)),
  );
  readonly semaines = computed(() => grouperParSemaine(this.journal()));
  readonly modeleParTitre = computed(() => modelesParTitre(this.routinesChoisies()));
  readonly toutEstCoche = computed(
    () => this.routines().length > 0 && this.routinesChoisies().length === this.routines().length,
  );
  readonly nbAutomatiques = computed(
    () => this.journal().filter((seance) => this.estAutomatique(seance)).length,
  );
  readonly nbAjoutees = computed(
    () => this.journal().filter((seance) => this.estAjoutee(seance)).length,
  );
  readonly rattachementsIncomplets = computed(
    () =>
      this.journal().filter((seance) => this.estAjoutee(seance) && this.cible(seance) === null)
        .length,
  );
  readonly peutAvancer = computed(() => {
    switch (this.etape()) {
      case 1:
        return this.modelesChoisis().size > 0;
      case 2:
        return this.rattachementsIncomplets() === 0;
      default:
        return this.nom().trim().length > 0;
    }
  });

  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private i18n: I18nService,
    private telechargementExport: TelechargementExport,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.exportId.set(id ? Number(id) : null);
    this.charger();
  }

  basculerRoutine(routine: RoutineChoix): void {
    this.modelesChoisis.update((choisis) => {
      const suivants = new Set(choisis);
      if (!suivants.delete(routine.id)) suivants.add(routine.id);
      return suivants;
    });
  }

  basculerToutesLesRoutines(): void {
    this.modelesChoisis.set(
      this.toutEstCoche() ? new Set() : new Set(this.routines().map((routine) => routine.id)),
    );
  }

  estCochee(routine: RoutineChoix): boolean {
    return this.modelesChoisis().has(routine.id);
  }

  modeleAutomatique(seance: SeanceFaite): number | null {
    return modeleAutomatique(seance, this.modelesChoisis(), this.modeleParTitre());
  }

  estAutomatique(seance: SeanceFaite): boolean {
    return this.modeleAutomatique(seance) !== null;
  }

  estAjoutee(seance: SeanceFaite): boolean {
    return !this.estAutomatique(seance) && this.ajoutees().has(seance.id);
  }

  cible(seance: SeanceFaite): number | null {
    const cible = this.ajoutees().get(seance.id) ?? null;
    return cible !== null && this.modelesChoisis().has(cible) ? cible : null;
  }

  titreDuModele(modeleId: number | null): string {
    return this.routines().find((routine) => routine.id === modeleId)?.titre ?? '';
  }

  basculerSeance(seance: SeanceFaite): void {
    this.ajoutees.update((courantes) => {
      const suivantes = new Map(courantes);
      if (!suivantes.delete(seance.id)) suivantes.set(seance.id, null);
      return suivantes;
    });
  }

  choisirCible(seance: SeanceFaite, valeur: string): void {
    const cible = valeur === '' ? null : Number(valeur);
    this.ajoutees.update((courantes) => new Map(courantes).set(seance.id, cible));
  }

  libelleSeance(seance: SeanceFaite): string {
    return seance.titre || this.i18n.t('journal.workout');
  }

  formaterJour(date: Date): string {
    return date.toLocaleDateString(this.locale(), {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
    });
  }

  formaterSemaine(date: Date): string {
    return date.toLocaleDateString(this.locale(), { day: 'numeric', month: 'long' });
  }

  precedent(): void {
    if (this.etape() === 1) {
      this.quitter();
      return;
    }
    this.erreur.set(null);
    this.etape.set((this.etape() - 1) as EtapeAssistant);
  }

  suivant(): void {
    if (!this.peutAvancer()) return;
    if (this.etape() === DERNIERE_ETAPE) {
      this.enregistrer();
      return;
    }
    const prochaine = (this.etape() + 1) as EtapeAssistant;
    if (prochaine === DERNIERE_ETAPE && this.nom().trim() === '') {
      this.nom.set(this.nomParDefaut());
    }
    this.etape.set(prochaine);
  }

  saisirNom(valeur: string): void {
    this.nom.set(valeur);
  }

  async telecharger(): Promise<void> {
    const classeur = this.resultat();
    if (!classeur || this.telechargementEnCours()) return;
    this.erreur.set(null);
    this.telechargementEnCours.set(true);
    try {
      await this.telechargementExport.telecharger(classeur);
    } catch {
      this.erreur.set(this.i18n.t('export.assistant.downloadError'));
    } finally {
      this.telechargementEnCours.set(false);
    }
  }

  quitter(): void {
    this.router.navigate(['/exports']);
  }

  private charger(): void {
    const username = this.authService.getUsername();
    if (!username) return;
    const id = this.exportId();
    forkJoin({
      programmes: this.chironApi.getProgrammes(username),
      historique: this.chironApi.getHistorique(username),
      classeur: id === null ? of(null) : this.chironApi.getExport(id),
    }).subscribe({
      next: ({ programmes, historique, classeur }) => {
        this.routines.set(programmes.filter((seance) => !seance.historique).map(versRoutine));
        this.journal.set(seancesTerminees(historique as SeanceJournalBrute[]));
        if (classeur) this.reprendre(classeur);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreurChargement.set(true);
      },
    });
  }

  private reprendre(existant: ExportProgramme): void {
    this.nom.set(existant.nom);
    this.modelesChoisis.set(new Set(existant.modeles.map((modele) => modele.id)));
    this.ajoutees.set(new Map(existant.seances.map((s) => [s.seanceId, s.modeleId])));
  }

  private enregistrer(): void {
    if (this.enregistrement()) return;
    this.erreur.set(null);
    this.enregistrement.set(true);
    const requete = this.requete();
    const id = this.exportId();
    const appel =
      id === null
        ? this.chironApi.creerExport(requete)
        : this.chironApi.modifierExport(id, requete);
    appel.subscribe({
      next: (classeur) => {
        this.enregistrement.set(false);
        this.resultat.set(classeur);
      },
      error: () => {
        this.enregistrement.set(false);
        this.erreur.set(this.i18n.t('export.assistant.errorGeneric'));
      },
    });
  }

  private requete(): ExportProgrammeRequete {
    const modeleIds = this.routinesChoisies().map((routine) => routine.id);
    const seances: SeanceExport[] = [];
    for (const seance of this.journal()) {
      const cible = this.cible(seance);
      if (this.estAjoutee(seance) && cible !== null) {
        seances.push({ seanceId: seance.id, modeleId: cible });
      }
    }
    return { nom: this.nom().trim(), modeleIds, seances };
  }

  private nomParDefaut(): string {
    return this.i18n.t('export.assistant.defaultName', {
      date: new Date().toLocaleDateString(this.locale(), {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      }),
    });
  }

  private locale(): string {
    return this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR';
  }
}

function versRoutine(seance: {
  id: number;
  titre?: string | null;
  exercices?: { series?: unknown[] | null }[] | null;
}): RoutineChoix {
  const exercices = seance.exercices ?? [];
  return {
    id: seance.id,
    titre: seance.titre?.trim() ?? '',
    nbExercices: exercices.length,
    nbSeries: exercices.reduce((total, exercice) => total + (exercice.series?.length ?? 0), 0),
  };
}
