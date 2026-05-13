import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';

export interface Routine {
  id: string;
  titre: string;
  sousTitre: string;
  totalSeries: number;
}

@Component({
  selector: 'app-programme',
  standalone: true,
  imports: [CommonModule, HeaderComponent],
  templateUrl: './programme.html',
  styleUrls: ['./programme.css'],
})
export class Programme implements OnInit {

  routines  = signal<Routine[]>([]);
  isLoading = signal(true);

  // ── Drag state ───────────────────────────────────────────────────────────────
  dragFromIdx  = signal(-1);
  dragOverIdx  = signal(-1);

  private _from           = -1;
  private _to             = -1;
  private _touchDragging  = false;
  private _longPressTimer: any = null;
  private _touchStartX    = 0;
  private _touchStartY    = 0;

  constructor(
    private router:      Router,
    private chironApi:   ChironApi,
    private authService: AuthService,
  ) {}

  ngOnInit() { this.chargerProgrammes(); }

  chargerProgrammes() {
    const username = this.authService.getUsername();
    if (!username) return;

    this.isLoading.set(true);
    this.chironApi.getProgrammes(username).subscribe({
      next: (data) => {
        const routinesFormatees = data.map((seance: any) => {
          let total = 0;
          seance.exercices?.forEach((exo: any) => { total += exo.series?.length ?? 0; });
          return { id: seance.id.toString(), titre: seance.titre, sousTitre: 'Séance', totalSeries: total };
        });
        this.routines.set(routinesFormatees);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false),
    });
  }

  commencerRoutine(id: string) { this.router.navigate(['/session', id]); }
  ajouterRoutine()             { this.router.navigate(['/session']); }
  editerRoutine(id: string)    { this.router.navigate(['/session', id]); }

  supprimerRoutine(routineId: string) {
    if (confirm('Es-tu sûr de vouloir supprimer ce programme ?')) {
      const username = this.authService.getUsername();
      if (!username) return;
      this.chironApi.deleteProgramme(parseInt(routineId), username).subscribe({
        next: () => this.routines.update(list => list.filter(r => r.id !== routineId)),
        error: () => alert('Impossible de supprimer cette routine.'),
      });
    }
  }

  // ── HTML5 Drag & Drop (desktop) ──────────────────────────────────────────────

  onDragStart(event: DragEvent, index: number, cardEl: HTMLElement) {
    this._from = index;
    this.dragFromIdx.set(index);
    event.dataTransfer?.setData('text/plain', String(index));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
    // Use the whole card as the ghost image
    event.dataTransfer?.setDragImage(cardEl, cardEl.offsetWidth / 2, 30);
  }

  onDragOver(event: DragEvent, index: number) {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
    this._to = index;
    this.dragOverIdx.set(index);
  }

  onDrop(event: DragEvent, index: number) {
    event.preventDefault();
    this._to = index;
    this._applyReorder();
  }

  onDragEnd() {
    this.dragFromIdx.set(-1);
    this.dragOverIdx.set(-1);
    this._from = -1;
    this._to   = -1;
  }

  // ── Touch drag (mobile long-press) ───────────────────────────────────────────

  onTouchStart(event: TouchEvent, index: number) {
    const t = event.touches[0];
    this._touchStartX = t.clientX;
    this._touchStartY = t.clientY;

    this._longPressTimer = setTimeout(() => {
      this._touchDragging = true;
      this._from = index;
      this.dragFromIdx.set(index);
      if ('vibrate' in navigator) (navigator as any).vibrate(30);
    }, 350);
  }

  onTouchMove(event: TouchEvent) {
    const t = event.touches[0];

    if (!this._touchDragging) {
      // Cancel long-press if finger moved significantly before 350ms
      if (Math.abs(t.clientX - this._touchStartX) > 8 || Math.abs(t.clientY - this._touchStartY) > 8) {
        clearTimeout(this._longPressTimer);
      }
      return;
    }

    // Find the card under the finger
    const el   = document.elementFromPoint(t.clientX, t.clientY) as HTMLElement;
    const card = el?.closest<HTMLElement>('[data-idx]');
    if (card) {
      const idx = parseInt(card.getAttribute('data-idx')!);
      if (!isNaN(idx)) {
        this._to = idx;
        this.dragOverIdx.set(idx);
      }
    }
  }

  onTouchEnd() {
    clearTimeout(this._longPressTimer);
    if (this._touchDragging) {
      this._applyReorder();
      this._touchDragging = false;
      this.dragFromIdx.set(-1);
      this.dragOverIdx.set(-1);
    }
  }

  // ── Shared reorder logic ─────────────────────────────────────────────────────

  private _applyReorder() {
    const from = this._from;
    const to   = this._to;
    if (from >= 0 && to >= 0 && from !== to) {
      const list = [...this.routines()];
      const [item] = list.splice(from, 1);
      list.splice(to, 0, item);
      this.routines.set(list);
    }
    this._from = -1;
    this._to   = -1;
  }
}
