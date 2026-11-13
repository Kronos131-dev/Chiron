import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ChironApi,
  CourseTraceDto,
  ExerciceDefinitionDto,
  SanteActiviteDto,
} from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { Router } from '@angular/router';
import { HeaderComponent } from '../shared/header/header';
import { durationMinutes, formatDuration } from '../../util/duration';
import { formaterAllure, formaterChrono } from '../../util/allure';
import { TraceSvg, projeterTrace } from '../../util/trace-svg';
import { GrapheAllure, construireGrapheAllure } from '../../util/graphe-allure';
import { CarteSeance } from '../../partage/carte';
import { PartageSeance } from '../../service/partage-seance';
import { PartageCarte } from '../shared/partage-carte/partage-carte';
import { ExerciceCardComponent } from '../shared/exercice-card/exercice-card';
import { ExercisePickerComponent } from '../shared/exercise-picker/exercise-picker';
import { ChampNote } from '../shared/champ-note/champ-note';
import {
  ExerciceForm,
  exerciceFormFromDto,
  makeEmptyExercice,
  serieFormToDto,
} from '../../shared/exercise-forms';
import { intercepterRetourNatif } from '../../service/plateforme';

const COTE_TRACE = 320;
const LARGEUR_GRAPHE = 320;
const HAUTEUR_GRAPHE = 110;

interface BrouillonSeance {
  id: number;
  titre: string;
  note: string | null | undefined;
  weekNumber: number;
  startTime: string;
  endTime: string | null;
  exercices: ExerciceForm[];
}

/** ISO-8601 week number (lundi = 1er jour de la semaine, semaine 1 = celle du premier jeudi). */
function getIsoWeekNumber(d: Date): number {
  const target = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const dayNr = (target.getUTCDay() + 6) % 7;
  target.setUTCDate(target.getUTCDate() - dayNr + 3);
  const firstThursday = new Date(Date.UTC(target.getUTCFullYear(), 0, 4));
  const firstThursdayDayNr = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstThursdayDayNr + 3);
  return 1 + Math.round((target.getTime() - firstThursday.getTime()) / (7 * 24 * 3600 * 1000));
}

/**
 * Component responsible for displaying (and now editing) the user's workout journal history.
 */
@Component({
  selector: 'app-journal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HeaderComponent,
    TranslatePipe,
    PartageCarte,
    ExerciceCardComponent,
    ExercisePickerComponent,
    ChampNote,
  ],
  templateUrl: './journal.html',
  styleUrls: ['./journal.css'],
})
export class Journal implements OnInit, OnDestroy {
  historique = signal<any[]>([]);
  historiqueGrouped = signal<any[]>([]);
  weeksToShow = signal(4);
  isLoading = signal(true);
  activiteParSeance = signal<Map<number, SanteActiviteDto>>(new Map());

  currentUsername: string | null = null;

  expandedSeanceId = signal<number | null>(null);

  /** Id of the session currently in edit mode, null otherwise. */
  editingSeanceId = signal<number | null>(null);

  /** Local mutable working copy of the session being edited. */
  editingDraft = signal<BrouillonSeance | null>(null);

  selecteurOuvert = signal(false);
  exercicesAjoutes = signal<ExerciceForm[]>([]);

  /** True while the save request is in flight. */
  isSaving = signal(false);

  tracesChargees = signal<Map<number, CourseTraceDto>>(new Map());
  erreurTrace = signal<number | null>(null);

  traceDepliee = signal<number | null>(null);

  carteAPartager = signal<CarteSeance | null>(null);
  partageEnPreparation = signal<number | null>(null);

  /** Transient status message (success / error). */
  saveStatus = signal<string | null>(null);
  private _saveStatusTimer: ReturnType<typeof setTimeout> | null = null;
  private libererRetourNatif: () => void = () => {};

  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router,
    public i18n: I18nService,
    private partage: PartageSeance,
  ) {}

  ngOnInit() {
    this.libererRetourNatif = intercepterRetourNatif(() => this.consommerRetour());
    this.currentUsername = this.authService.getUsername();
    if (this.currentUsername) {
      this.loadJournal(this.currentUsername);
    } else {
      this.isLoading.set(false);
      console.error('Aucun utilisateur connecté !');
    }
  }

  ngOnDestroy() {
    this.libererRetourNatif();
  }

  consommerRetour(): boolean {
    if (this.selecteurOuvert()) {
      this.fermerSelecteur();
      return true;
    }
    if (this.editingSeanceId() !== null) {
      this.cancelEdit();
      return true;
    }
    return false;
  }

  loadJournal(username: string) {
    this.isLoading.set(true);

    this.chironApi.getHistorique(username).subscribe({
      next: (data) => {
        this.historique.set(data);
        this.groupHistoriqueByWeekAndDay(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Erreur lors du chargement du journal', err);
        this.isLoading.set(false);
      },
    });

    this.chironApi.getActivites(400, 'CHIRON_MUSCU').subscribe({
      next: (activites) => this.activiteParSeance.set(this.groupBySeanceId(activites)),
      error: (err) => console.error('Erreur lors du chargement des activités montre', err),
    });
  }

  private groupBySeanceId(activites: SanteActiviteDto[]): Map<number, SanteActiviteDto> {
    const map = new Map<number, SanteActiviteDto>();
    for (const activite of activites) {
      if (activite.seanceId != null) map.set(activite.seanceId, activite);
    }
    return map;
  }

  groupHistoriqueByWeekAndDay(data: any[]) {
    // Regroupement basé sur la date ISO-8601 (lundi-dimanche), pas sur le champ
    // weekNumber stocké en base — d'anciennes séances ont un numéro hérité d'un
    // calcul JS naïf qui ne s'alignait pas sur le lundi.
    const groupedByWeek: Record<string, any[]> = {};
    for (const seance of data) {
      const d = new Date(seance.startTime);
      const week = getIsoWeekNumber(d);
      const key = String(week);
      if (!groupedByWeek[key]) groupedByWeek[key] = [];
      groupedByWeek[key].push(seance);
    }

    const result = Object.keys(groupedByWeek)
      .sort((a, b) => parseInt(b) - parseInt(a))
      .map((week) => ({
        weekNumber: week,
        seances: groupedByWeek[week].sort(
          (a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime(),
        ),
      }));

    this.historiqueGrouped.set(result);
  }

  loadMoreWeeks() {
    this.weeksToShow.update((v) => v + 4);
  }

  visibleHistory() {
    return this.historiqueGrouped().slice(0, this.weeksToShow());
  }

  hasMoreWeeks() {
    return this.historiqueGrouped().length > this.weeksToShow();
  }

  toggleSeanceDetails(id: number) {
    if (this.editingSeanceId() === id) return; // Don't collapse while editing
    if (this.expandedSeanceId() === id) {
      this.expandedSeanceId.set(null);
    } else {
      this.expandedSeanceId.set(id);
    }
  }

  supprimerSeance(seanceId: number, event: Event) {
    event.stopPropagation();
    if (confirm(this.i18n.t('journal.deleteConfirm'))) {
      const username = this.authService.getUsername();
      if (!username) return;

      this.chironApi.deleteProgramme(seanceId, username).subscribe({
        next: () => {
          this.loadJournal(username);
        },
        error: (err) => {
          console.error('Erreur lors de la suppression', err);
          alert(this.i18n.t('journal.deleteError'));
        },
      });
    }
  }

  /** Enter edit mode: deep-clone the seance into editingDraft. */
  startEdit(seance: any, event: Event) {
    event.stopPropagation();
    this.expandedSeanceId.set(seance.id);
    this.editingSeanceId.set(seance.id);
    this.editingDraft.set({
      id: seance.id,
      titre: seance.titre ?? '',
      note: seance.note ?? '',
      weekNumber: seance.weekNumber,
      startTime: seance.startTime,
      endTime: seance.endTime ?? null,
      exercices: (seance.exercices ?? []).map(exerciceFormFromDto),
    });
  }

  cancelEdit(event?: Event) {
    if (event) event.stopPropagation();
    this.fermerSelecteur();
    this.editingSeanceId.set(null);
    this.editingDraft.set(null);
  }

  ouvrirSelecteur() {
    this.exercicesAjoutes.set([]);
    this.selecteurOuvert.set(true);
  }

  fermerSelecteur() {
    this.selecteurOuvert.set(false);
    this.exercicesAjoutes.set([]);
  }

  ajouterExerciceDepuisBibliotheque(definition: ExerciceDefinitionDto) {
    const exercice = makeEmptyExercice(
      this.i18n.pick(definition.nomFr, definition.nomEn),
      definition.id,
      definition.cardioType as ExerciceForm['cardioType'],
      definition.wodType as ExerciceForm['wodType'],
    );
    this.ecrireExercicesDuBrouillon((exercices) => [...exercices, exercice]);
    this.exercicesAjoutes.update((ajoutes) => [...ajoutes, exercice]);
  }

  ajouterExerciceLibre() {
    this.ecrireExercicesDuBrouillon((exercices) => [...exercices, makeEmptyExercice()]);
    this.fermerSelecteur();
  }

  retirerExercice(exerciceId: ExerciceForm['id']) {
    this.ecrireExercicesDuBrouillon((exercices) => exercices.filter((e) => e.id !== exerciceId));
    this.exercicesAjoutes.update((ajoutes) => ajoutes.filter((e) => e.id !== exerciceId));
  }

  private ecrireExercicesDuBrouillon(transformer: (exercices: ExerciceForm[]) => ExerciceForm[]) {
    this.editingDraft.update((brouillon) =>
      brouillon ? { ...brouillon, exercices: transformer(brouillon.exercices) } : brouillon,
    );
  }

  saveEdit(event: Event) {
    event.stopPropagation();
    const draft = this.editingDraft();
    const username = this.authService.getUsername();
    if (!draft || !username) return;

    this.isSaving.set(true);
    this.chironApi.sauvegarderProgramme(username, this.seanceAEnregistrer(draft)).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.editingSeanceId.set(null);
        this.editingDraft.set(null);
        this.flashStatus(this.i18n.t('journal.updated'));
        this.loadJournal(username);
      },
      error: (err) => {
        console.error('Erreur lors de la modification', err);
        this.isSaving.set(false);
        this.flashStatus(this.i18n.t('journal.updateError'));
      },
    });
  }

  private seanceAEnregistrer(draft: BrouillonSeance) {
    return {
      id: draft.id,
      titre: draft.titre,
      note: draft.note || null,
      weekNumber: draft.weekNumber,
      startTime: draft.startTime,
      endTime: draft.endTime,
      historique: true,
      exercices: draft.exercices.map((exo) => ({
        nom: exo.nom,
        commentaire: exo.commentaire ?? '',
        unilateral: exo.unilateral ?? false,
        exerciceDefinitionId: exo.definitionId ?? null,
        blockId: exo.blockId ?? null,
        blockType: exo.blockType ?? null,
        series: exo.series.map(serieFormToDto),
      })),
    };
  }

  private flashStatus(msg: string) {
    this.saveStatus.set(msg);
    if (this._saveStatusTimer) clearTimeout(this._saveStatusTimer);
    this._saveStatusTimer = setTimeout(() => this.saveStatus.set(null), 3000);
  }

  formatDate(dateString: string): string {
    const options: Intl.DateTimeFormatOptions = {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    };
    return new Date(dateString).toLocaleDateString(
      this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR',
      options,
    );
  }

  /**
   * Durée formatée d'une séance (« 72 min »), ou null si non chronométrée.
   *
   * @param startTime Heure de début (ISO) de la séance.
   * @param endTime   Heure de fin (ISO) de la séance.
   * @return La durée formatée, ou null si la durée n'est pas calculable.
   */
  dureeSeance(startTime: string | null, endTime?: string | null): string | null {
    const min = durationMinutes(startTime, endTime);
    return min == null ? null : formatDuration(min);
  }

  partagerSeance(seance: any, event: Event): void {
    event.stopPropagation();
    if (this.partageEnPreparation() !== null) return;
    this.partageEnPreparation.set(seance.id);
    this.partage
      .preparer(seance, this.activiteMontre(seance.id) ?? null, this.tracesChargees())
      .subscribe({
        next: (carte) => {
          this.carteAPartager.set(carte);
          this.partageEnPreparation.set(null);
        },
        error: () => this.partageEnPreparation.set(null),
      });
  }

  activiteMontre(seanceId: number): SanteActiviteDto | undefined {
    return this.activiteParSeance().get(seanceId);
  }

  zonesActivite(activite: SanteActiviteDto): { key: string; minutes: number; color: string }[] {
    return [
      {
        key: 'journal.activite.zoneLow',
        minutes: activite.minutesZoneBasse ?? 0,
        color: '#22d3ee',
      },
      {
        key: 'journal.activite.zoneModerate',
        minutes: activite.minutesZoneBruleuse ?? 0,
        color: '#2dd4bf',
      },
      {
        key: 'journal.activite.zoneIntense',
        minutes: activite.minutesZoneCardio ?? 0,
        color: '#fbbf24',
      },
      { key: 'journal.activite.zoneMax', minutes: activite.minutesZonePic ?? 0, color: '#f87171' },
    ];
  }

  totalMinutesZonesActivite(activite: SanteActiviteDto): number {
    return this.zonesActivite(activite).reduce((sum, z) => sum + z.minutes, 0) || 1;
  }

  hasZonesActivite(activite: SanteActiviteDto): boolean {
    return this.zonesActivite(activite).some((z) => z.minutes > 0);
  }

  // WHY: une trace pèse quelques dizaines de milliers de points. Elle n'est demandée qu'au
  // moment où l'athlète veut la voir, et jamais deux fois pour la même sortie.
  basculerTrace(courseTraceId: number): void {
    if (this.traceDepliee() === courseTraceId) {
      this.traceDepliee.set(null);
      return;
    }
    this.traceDepliee.set(courseTraceId);
    if (this.tracesChargees().has(courseTraceId)) return;

    this.erreurTrace.set(null);
    this.chironApi.getTraceCourse(courseTraceId).subscribe({
      next: (trace) => {
        this.tracesChargees.update((cache) => new Map(cache).set(courseTraceId, trace));
      },
      // WHY: refermer le volet en silence rendait le bouton mort — l'athlète appuie sur
      // « Voir le tracé » et rien ne bouge, sans jamais apprendre que c'est la requête qui
      // a échoué et non sa sortie qui a été perdue.
      error: () => this.erreurTrace.set(courseTraceId),
    });
  }

  trace(courseTraceId: number): CourseTraceDto | undefined {
    return this.tracesChargees().get(courseTraceId);
  }

  traceSvg(courseTraceId: number): TraceSvg {
    const trace = this.trace(courseTraceId);
    return projeterTrace(trace?.points ?? [], COTE_TRACE);
  }

  grapheAllure(courseTraceId: number): GrapheAllure | null {
    const trace = this.trace(courseTraceId);
    return construireGrapheAllure(trace?.points ?? [], LARGEUR_GRAPHE, HAUTEUR_GRAPHE);
  }

  couleurSegment(courseTraceId: number, allureKmh: number): string {
    const { allureMinKmh, allureMaxKmh } = this.traceSvg(courseTraceId);
    if (allureMaxKmh <= allureMinKmh) return 'hsl(200 90% 60%)';
    const ratio = Math.min(
      1,
      Math.max(0, (allureKmh - allureMinKmh) / (allureMaxKmh - allureMinKmh)),
    );
    return `hsl(${Math.round(220 - 220 * ratio)} 90% ${Math.round(52 + 12 * ratio)}%)`;
  }

  allureFormatee(kmh: number): string {
    return formaterAllure(kmh);
  }

  chronoFormate(secondes: number): string {
    return formaterChrono(secondes);
  }
}
