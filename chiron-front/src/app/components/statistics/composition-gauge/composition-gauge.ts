import { Component, computed, input, signal } from '@angular/core';
import { GaugeSpec } from '../composition-gauges.config';

/**
 * Jauge horizontale à trois zones (faible / moyenne / élevée) pour une métrique de
 * composition corporelle. Affiche une flèche positionnée sur la valeur de
 * l'utilisateur, les frontières des zones et une bulle expliquant le sigle.
 *
 * Les couleurs des zones dépendent du sens de désirabilité (`spec.dir`) : la zone
 * « saine » est toujours en vert.
 */
@Component({
  selector: 'app-composition-gauge',
  standalone: true,
  template: `
    <div class="bg-slate-950/40 border border-white/5 rounded-lg px-3 py-3 relative">
      <!-- En-tête : sigle + libellé + info -->
      <div class="flex items-center gap-1.5 mb-1">
        <span class="text-white font-headline text-sm font-bold leading-none">{{ acronym() }}</span>
        <span class="text-on-surface-variant text-[10px] leading-none truncate">{{ label() }}</span>
        <button type="button" (click)="toggle()" [title]="explanation()"
                [attr.aria-label]="'Explication ' + acronym()"
                class="ml-auto shrink-0 text-on-surface-variant/70 hover:text-secondary transition-colors">
          <span class="material-symbols-outlined text-[18px] leading-none align-middle">info</span>
        </button>
      </div>

      <!-- Bulle d'explication (ne déborde pas de l'écran sur mobile) -->
      @if (open()) {
        <div class="absolute z-20 right-2 left-2 sm:left-auto top-9 sm:w-64 bg-slate-800 border border-white/10 rounded-lg p-3 text-[11px] leading-relaxed text-white shadow-xl">
          <span class="font-bold text-secondary">{{ acronym() }}</span> — {{ explanation() }}
        </div>
      }

      <!-- Jauge -->
      <div class="relative mt-9">
        <!-- Flèche + valeur de l'utilisateur (alignement adapté près des bords) -->
        <div class="absolute bottom-full mb-0.5 flex flex-col items-center pointer-events-none"
             [style.left.%]="markerPct()" [style.transform]="markerStyle()">
          <span class="font-headline text-xs font-bold whitespace-nowrap leading-none" [style.color]="markerColor()">
            {{ fmt(value()) }}{{ unit() ? ' ' + unit() : '' }}
          </span>
        </div>
        <div class="absolute bottom-full -mb-1 leading-none text-[11px] pointer-events-none"
             [style.left.%]="markerPct()" style="transform: translateX(-50%);" [style.color]="markerColor()">▼</div>

        <!-- Barre des trois zones -->
        <div class="flex h-2.5 rounded-full overflow-hidden">
          <div [style.width.%]="w1()" [style.backgroundColor]="colors()[0]"></div>
          <div [style.width.%]="w2()" [style.backgroundColor]="colors()[1]"></div>
          <div [style.width.%]="w3()" [style.backgroundColor]="colors()[2]"></div>
        </div>

        <!-- Valeurs des frontières -->
        <div class="relative h-3 mt-1 text-[9px] text-on-surface-variant/80">
          <span class="absolute left-0">{{ fmt(scaleMin()) }}</span>
          <span class="absolute" [style.left.%]="pctB1()" style="transform: translateX(-50%);">{{ fmt(spec().b1) }}</span>
          <span class="absolute" [style.left.%]="pctB2()" style="transform: translateX(-50%);">{{ fmt(spec().b2) }}</span>
          <span class="absolute right-0">{{ fmt(scaleMax()) }}</span>
        </div>

        <!-- Libellés des zones -->
        <div class="flex text-[8px] uppercase tracking-wider text-on-surface-variant/50 mt-0.5">
          <div [style.width.%]="w1()" class="text-center overflow-hidden text-ellipsis whitespace-nowrap px-0.5">Faible</div>
          <div [style.width.%]="w2()" class="text-center overflow-hidden text-ellipsis whitespace-nowrap px-0.5">Moyenne</div>
          <div [style.width.%]="w3()" class="text-center overflow-hidden text-ellipsis whitespace-nowrap px-0.5">Élevée</div>
        </div>
      </div>
    </div>
  `,
})
export class CompositionGaugeComponent {
  acronym = input.required<string>();
  label = input.required<string>();
  unit = input<string>('');
  value = input.required<number>();
  spec = input.required<GaugeSpec>();
  explanation = input.required<string>();

  open = signal(false);
  toggle(): void {
    this.open.update((v) => !v);
  }

  private readonly C = { green: '#34d399', amber: '#fbbf24', red: '#f87171' };

  /** Échelle fixe (bornes du spec) : positions et libellés restent prévisibles. */
  private bounds = computed(() => {
    const s = this.spec();
    const min = s.scaleMin;
    const max = s.scaleMax > s.scaleMin ? s.scaleMax : s.scaleMin + 1;
    return { min, max };
  });

  private pct(x: number): number {
    const { min, max } = this.bounds();
    return Math.max(0, Math.min(100, ((x - min) / (max - min)) * 100));
  }

  scaleMin = computed(() => this.bounds().min);
  scaleMax = computed(() => this.bounds().max);

  pctB1 = computed(() => this.pct(this.spec().b1));
  pctB2 = computed(() => this.pct(this.spec().b2));
  markerPct = computed(() => this.pct(this.value()));

  /** Garde la bulle de valeur dans le cadre quand la flèche est près d'un bord. */
  markerStyle = computed(() => {
    const p = this.markerPct();
    if (p <= 12) return 'translateX(-8px)';
    if (p >= 88) return 'translateX(calc(-100% + 8px))';
    return 'translateX(-50%)';
  });

  w1 = computed(() => this.pctB1());
  w2 = computed(() => this.pctB2() - this.pctB1());
  w3 = computed(() => 100 - this.pctB2());

  colors = computed<[string, string, string]>(() => {
    switch (this.spec().dir) {
      case 'highGood':
        return [this.C.red, this.C.amber, this.C.green];
      case 'lowGood':
        return [this.C.green, this.C.amber, this.C.red];
      default:
        return [this.C.amber, this.C.green, this.C.amber];
    }
  });

  /** Index de la zone où se situe la valeur (0 faible, 1 moyenne, 2 élevée). */
  zoneIndex = computed(() => {
    const s = this.spec();
    const v = this.value();
    return v < s.b1 ? 0 : v < s.b2 ? 1 : 2;
  });

  markerColor = computed(() => this.colors()[this.zoneIndex()]);

  /** Arrondi lisible : entier au-delà de 100, sinon une décimale. */
  fmt(v: number): string {
    const r = Math.abs(v) >= 100 ? Math.round(v) : Math.round(v * 10) / 10;
    return String(r);
  }
}
