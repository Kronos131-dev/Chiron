import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  ChironApi,
  NiveauExperience,
  ObjectifPrincipal,
  Sexe,
  TypeEquipement,
  UserProfileSetup,
} from '../../service/chiron-api';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './onboarding.html',
  styleUrls: ['./onboarding.css'],
})
export class OnboardingComponent implements OnInit {

  step = signal(1);
  totalSteps = 4;
  saving = signal(false);
  error = signal<string | null>(null);

  // Form fields
  dateNaissance = signal<string>(''); // YYYY-MM-DD
  sexe = signal<Sexe | null>(null);
  tailleCm = signal<number | null>(null);
  poidsCorps = signal<number | null>(null);
  niveauExperience = signal<NiveauExperience | null>(null);
  objectifPrincipal = signal<ObjectifPrincipal | null>(null);
  frequenceVisee = signal<number | null>(3);
  materielDisponible = signal<TypeEquipement[]>(['POIDS_DU_CORPS']);
  blessures = signal<string>('');
  preferences = signal<string>('');

  // Les labels sont des clés i18n rendues via `| t` dans le template.
  readonly sexes: { value: Sexe; label: string }[] = [
    { value: 'HOMME', label: 'onboarding.sexe.HOMME' },
    { value: 'FEMME', label: 'onboarding.sexe.FEMME' },
    { value: 'AUTRE', label: 'onboarding.sexe.AUTRE' },
  ];

  readonly niveaux: { value: NiveauExperience; label: string; hint: string }[] = [
    { value: 'DEBUTANT', label: 'onboarding.niveau.DEBUTANT', hint: 'onboarding.niveau.DEBUTANT.hint' },
    { value: 'INTERMEDIAIRE', label: 'onboarding.niveau.INTERMEDIAIRE', hint: 'onboarding.niveau.INTERMEDIAIRE.hint' },
    { value: 'AVANCE', label: 'onboarding.niveau.AVANCE', hint: 'onboarding.niveau.AVANCE.hint' },
    { value: 'EXPERT', label: 'onboarding.niveau.EXPERT', hint: 'onboarding.niveau.EXPERT.hint' },
  ];

  readonly objectifs: { value: ObjectifPrincipal; label: string }[] = [
    { value: 'FORCE', label: 'onboarding.objectif.FORCE' },
    { value: 'HYPERTROPHIE', label: 'onboarding.objectif.HYPERTROPHIE' },
    { value: 'ENDURANCE', label: 'onboarding.objectif.ENDURANCE' },
    { value: 'PERTE_DE_GRAS', label: 'onboarding.objectif.PERTE_DE_GRAS' },
    { value: 'MAINTIEN', label: 'onboarding.objectif.MAINTIEN' },
    { value: 'SANTE_GENERALE', label: 'onboarding.objectif.SANTE_GENERALE' },
  ];

  readonly materiels: { value: TypeEquipement; label: string }[] = [
    { value: 'POIDS_DU_CORPS', label: 'equip.POIDS_DU_CORPS' },
    { value: 'HALTERES', label: 'equip.HALTERES' },
    { value: 'BARRE', label: 'equip.BARRE' },
    { value: 'MACHINE', label: 'equip.MACHINE' },
    { value: 'POULIE', label: 'equip.POULIE' },
    { value: 'KETTLEBELL', label: 'equip.KETTLEBELL' },
    { value: 'ELASTIQUE', label: 'equip.ELASTIQUE' },
    { value: 'BARRE_FIXE', label: 'equip.BARRE_FIXE' },
    { value: 'ANNEAUX', label: 'equip.ANNEAUX' },
    { value: 'AUTRE', label: 'equip.AUTRE' },
  ];

  canGoNext = computed(() => {
    const s = this.step();
    if (s === 1) {
      return !!this.dateNaissance() && !!this.sexe() && (this.tailleCm() ?? 0) > 0;
    }
    if (s === 2) {
      return !!this.niveauExperience() && !!this.objectifPrincipal();
    }
    if (s === 3) {
      return (this.frequenceVisee() ?? 0) >= 1 && this.materielDisponible().length > 0;
    }
    return true;
  });

  constructor(private router: Router, private chironApi: ChironApi, public i18n: I18nService) {}

  ngOnInit() {
    // /onboarding sert à la fois pour le premier login et pour l'édition ultérieure.
    // On pré-remplit toujours avec les valeurs existantes ; l'utilisateur peut sortir
    // en re-cliquant sur "Forger mon profil" sans changer.
    this.chironApi.getProfileSetup().subscribe({
      next: (setup) => {
        if (setup.dateNaissance) this.dateNaissance.set(setup.dateNaissance);
        if (setup.sexe) this.sexe.set(setup.sexe);
        if (setup.tailleCm != null) this.tailleCm.set(setup.tailleCm);
        if (setup.poidsCorps != null) this.poidsCorps.set(setup.poidsCorps);
        if (setup.niveauExperience) this.niveauExperience.set(setup.niveauExperience);
        if (setup.objectifPrincipal) this.objectifPrincipal.set(setup.objectifPrincipal);
        if (setup.frequenceVisee != null) this.frequenceVisee.set(setup.frequenceVisee);
        if (setup.materielDisponible?.length) this.materielDisponible.set(setup.materielDisponible);
        if (setup.blessures) this.blessures.set(setup.blessures);
        if (setup.preferences) this.preferences.set(setup.preferences);
      },
      error: () => {
        // si l'endpoint échoue (non authentifié, etc.), retour login
        this.router.navigate(['/login']);
      }
    });
  }

  next() {
    if (!this.canGoNext()) return;
    if (this.step() < this.totalSteps) {
      this.step.update(v => v + 1);
    } else {
      this.submit();
    }
  }

  prev() {
    if (this.step() > 1) {
      this.step.update(v => v - 1);
    }
  }

  toggleMateriel(m: TypeEquipement) {
    const current = this.materielDisponible();
    if (current.includes(m)) {
      this.materielDisponible.set(current.filter(x => x !== m));
    } else {
      this.materielDisponible.set([...current, m]);
    }
  }

  isMaterielSelected(m: TypeEquipement): boolean {
    return this.materielDisponible().includes(m);
  }

  submit() {
    this.saving.set(true);
    this.error.set(null);
    const payload: UserProfileSetup = {
      isOnboarded: true,
      dateNaissance: this.dateNaissance() || null,
      sexe: this.sexe(),
      tailleCm: this.tailleCm(),
      poidsCorps: this.poidsCorps(),
      niveauExperience: this.niveauExperience(),
      objectifPrincipal: this.objectifPrincipal(),
      frequenceVisee: this.frequenceVisee(),
      materielDisponible: this.materielDisponible(),
      blessures: this.blessures()?.trim() || null,
      preferences: this.preferences()?.trim() || null,
    };
    this.chironApi.saveProfileSetup(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigate(['/chat']);
      },
      error: () => {
        this.saving.set(false);
        this.error.set(this.i18n.t('onboarding.saveError'));
      }
    });
  }
}
