import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { Router } from '@angular/router';
import { HeaderComponent } from '../shared/header/header';
import { durationMinutes, formatDuration } from '../../util/duration';

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
  imports: [CommonModule, FormsModule, HeaderComponent, TranslatePipe],
  templateUrl: './journal.html',
  styleUrls: ['./journal.css']
})
export class Journal implements OnInit {
  historique = signal<any[]>([]);
  historiqueGrouped = signal<any[]>([]);
  isLoading = signal(true);

  currentUsername: string | null = null;

  expandedSeanceId = signal<number | null>(null);

  /** Id of the session currently in edit mode, null otherwise. */
  editingSeanceId = signal<number | null>(null);

  /** Local mutable working copy of the session being edited. */
  editingDraft = signal<any | null>(null);

  /** True while the save request is in flight. */
  isSaving = signal(false);

  /** Transient status message (success / error). */
  saveStatus = signal<string | null>(null);
  private _saveStatusTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router,
    public i18n: I18nService
  ) {}

  ngOnInit() {
    this.currentUsername = this.authService.getUsername();
    if (this.currentUsername) {
      this.loadJournal(this.currentUsername);
    } else {
      this.isLoading.set(false);
      console.error("Aucun utilisateur connecté !");
    }
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
        console.error("Erreur lors du chargement du journal", err);
        this.isLoading.set(false);
      }
    });
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
      .map(week => ({
        weekNumber: week,
        seances: groupedByWeek[week].sort((a, b) =>
          new Date(b.startTime).getTime() - new Date(a.startTime).getTime()
        )
      }));

    this.historiqueGrouped.set(result);
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
          console.error("Erreur lors de la suppression", err);
          alert(this.i18n.t('journal.deleteError'));
        }
      });
    }
  }

  /** Enter edit mode: deep-clone the seance into editingDraft. */
  startEdit(seance: any, event: Event) {
    event.stopPropagation();
    this.expandedSeanceId.set(seance.id);
    this.editingSeanceId.set(seance.id);
    this.editingDraft.set(this.cloneSeance(seance));
  }

  cancelEdit(event?: Event) {
    if (event) event.stopPropagation();
    this.editingSeanceId.set(null);
    this.editingDraft.set(null);
  }

  /** Add a new empty series to the given exercise in the draft. */
  addSerie(exoIndex: number, event: Event) {
    event.stopPropagation();
    const draft = this.editingDraft();
    if (!draft) return;
    const exos = [...draft.exercices];
    const exo = { ...exos[exoIndex], series: [...exos[exoIndex].series] };
    exo.series.push({ id: null, reps: 0, poids: 0, degressifs: [] });
    exos[exoIndex] = exo;
    this.editingDraft.set({ ...draft, exercices: exos });
  }

  removeSerie(exoIndex: number, serieIndex: number, event: Event) {
    event.stopPropagation();
    const draft = this.editingDraft();
    if (!draft) return;
    const exos = [...draft.exercices];
    const exo = { ...exos[exoIndex], series: [...exos[exoIndex].series] };
    exo.series.splice(serieIndex, 1);
    exos[exoIndex] = exo;
    this.editingDraft.set({ ...draft, exercices: exos });
  }

  /** ngModel two-way bindings need direct mutation — emit a signal write on each blur to keep things tidy. */
  onFieldChange() {
    // Trigger an explicit signal write so anything reading editingDraft re-evaluates.
    this.editingDraft.set({ ...this.editingDraft() });
  }

  saveEdit(event: Event) {
    event.stopPropagation();
    const draft = this.editingDraft();
    const username = this.authService.getUsername();
    if (!draft || !username) return;

    const dto = {
      id: draft.id,
      titre: draft.titre,
      weekNumber: draft.weekNumber,
      startTime: draft.startTime,
      isModele: true,
      exercices: draft.exercices.map((exo: any) => ({
        id: exo.id ?? null,
        nom: exo.nom,
        commentaire: exo.commentaire ?? '',
        exerciceDefinitionId: exo.exerciceDefinitionId ?? null,
        series: (exo.series || []).map((s: any) => ({
          id: s.id ?? null,
          poids: s.poids != null ? Number(s.poids) : 0,
          reps: s.reps != null ? Number(s.reps) : 0,
          commentaire: s.commentaire ?? '',
          degressifs: (s.degressifs || []).map((d: any) => ({
            id: d.id ?? null,
            poids: d.poids != null ? Number(d.poids) : 0,
            reps: d.reps != null ? Number(d.reps) : 0
          }))
        }))
      }))
    };

    this.isSaving.set(true);
    this.chironApi.sauvegarderProgramme(username, dto).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.editingSeanceId.set(null);
        this.editingDraft.set(null);
        this.flashStatus(this.i18n.t('journal.updated'));
        this.loadJournal(username);
      },
      error: (err) => {
        console.error("Erreur lors de la modification", err);
        this.isSaving.set(false);
        this.flashStatus(this.i18n.t('journal.updateError'));
      }
    });
  }

  private flashStatus(msg: string) {
    this.saveStatus.set(msg);
    if (this._saveStatusTimer) clearTimeout(this._saveStatusTimer);
    this._saveStatusTimer = setTimeout(() => this.saveStatus.set(null), 3000);
  }

  private cloneSeance(seance: any): any {
    return {
      ...seance,
      exercices: (seance.exercices || []).map((exo: any) => ({
        ...exo,
        series: (exo.series || []).map((s: any) => ({
          ...s,
          degressifs: (s.degressifs || []).map((d: any) => ({ ...d }))
        }))
      }))
    };
  }

  formatDate(dateString: string): string {
    const options: Intl.DateTimeFormatOptions = {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
    };
    return new Date(dateString).toLocaleDateString(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR', options);
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
}
