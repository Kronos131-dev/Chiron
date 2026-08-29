import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChironApi, NutritionLinkStatus, FitbitLinkStatus, TrainingPrefs, AiProvider } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { I18nService, Lang } from '../../service/i18n.service';
import { estNatif, ouvrirDansUnOnglet } from '../../service/plateforme';
import { ChironCourse } from '../../service/chiron-course.plugin';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, TranslatePipe],
  templateUrl: './settings.html',
})
export class Settings implements OnInit, OnDestroy {
  username: string;
  currentEmail = signal<string | null>(null);
  currentPrenom = signal<string | null>(null);
  currentNom = signal<string | null>(null);

  openSection = signal<'password' | 'email' | 'username' | 'identity' | 'tonnage' | 'ai' | 'language' | 'delete' | null>(null);

  /** Langue active (signal du service i18n), exposée au template via `lang()`. */
  get lang() { return this.i18n.lang; }

  // --- Préférences de calcul du tonnage ---
  halteresParImplement = signal(true);
  machineParCote = signal(false);

  // --- Fournisseur d'IA du coach ---
  aiProvider = signal<AiProvider>('MISTRAL');
  /** Gemini réellement disponible côté serveur (clé configurée) ; sinon repli Mistral. */
  geminiAvailable = signal(false);

  // Champs password
  currentPasswordForPwd = '';
  newPassword = '';
  confirmPassword = '';

  // Champs email / username
  newEmail = '';
  newUsername = '';

  // Champs identité (prénom / nom)
  newPrenom = '';
  newNom = '';

  // Suppression
  deleteConfirm = '';

  successMessage = signal('');
  errorMessage = signal('');
  isLoading = signal(false);

  // --- Liaison Olympus ---
  nutritionStatus = signal<NutritionLinkStatus | null>(null);
  olympusPseudo = signal('');
  olympusPassword = signal('');
  isLinking = signal(false);
  linkError = signal<string | null>(null);

  // --- Liaison Fitbit ---
  fitbitStatus = signal<FitbitLinkStatus | null>(null);
  isFitbitConnecting = signal(false);
  fitbitError = signal<string | null>(null);
  private fitbitPollHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router,
    public i18n: I18nService
  ) {
    this.username = this.authService.getUsername() || '';
    this.newUsername = this.username;
    this.chironApi.getSettingsInfo().subscribe({
      next: (info) => {
        this.currentEmail.set(info.email);
        this.newEmail = info.email ?? '';
        this.currentPrenom.set(info.prenom);
        this.currentNom.set(info.nom);
        this.newPrenom = info.prenom ?? '';
        this.newNom = info.nom ?? '';
      }
    });
  }

  ngOnInit() {
    this.loadNutritionStatus();
    this.loadFitbitStatus();
    this.loadTrainingPrefs();
    this.loadAiProvider();
  }

  private loadAiProvider() {
    this.chironApi.getAiProvider().subscribe({
      next: (pref) => {
        this.aiProvider.set(pref.provider);
        this.geminiAvailable.set(pref.geminiAvailable);
      },
      error: () => {} // garde le défaut (MISTRAL) en cas d'échec
    });
  }

  /** Change le fournisseur d'IA du coach et le persiste immédiatement. */
  setAiProvider(provider: AiProvider) {
    if (provider === 'GEMINI' && !this.geminiAvailable()) {
      this.errorMessage.set(this.i18n.t('settings.ai.notConfigured'));
      return;
    }
    if (this.aiProvider() === provider) return;
    const previous = this.aiProvider();
    this.aiProvider.set(provider);
    this.chironApi.updateAiProvider(provider).subscribe({
      next: () => {
        this.successMessage.set(this.i18n.t('settings.ai.updated', { provider }));
        setTimeout(() => this.successMessage.set(''), 2500);
      },
      error: () => {
        this.aiProvider.set(previous);
        this.errorMessage.set(this.i18n.t('settings.ai.error'));
      },
    });
  }

  /** Change la langue de l'application (switch à chaud) et la persiste. */
  setLanguage(l: Lang) {
    if (this.i18n.lang() === l) return;
    this.i18n.setLang(l);
    this.successMessage.set(this.i18n.t('settings.language.updated'));
    setTimeout(() => this.successMessage.set(''), 2500);
  }

  private loadTrainingPrefs() {
    this.chironApi.getTrainingPrefs().subscribe({
      next: (prefs) => {
        this.halteresParImplement.set(prefs.halteresParImplement);
        this.machineParCote.set(prefs.machineParCote);
      },
      error: () => {} // garde les défauts en cas d'échec
    });
  }

  /** Persiste les préférences de tonnage à chaque changement de toggle. */
  saveTrainingPrefs() {
    const prefs: TrainingPrefs = {
      halteresParImplement: this.halteresParImplement(),
      machineParCote: this.machineParCote(),
    };
    this.chironApi.updateTrainingPrefs(prefs).subscribe({
      next: () => {
        this.successMessage.set(this.i18n.t('settings.tonnage.saved'));
        setTimeout(() => this.successMessage.set(''), 2500);
      },
      error: () => this.errorMessage.set(this.i18n.t('settings.tonnage.error')),
    });
  }

  toggleHalteres() {
    this.halteresParImplement.update(v => !v);
    this.saveTrainingPrefs();
  }

  toggleMachine() {
    this.machineParCote.update(v => !v);
    this.saveTrainingPrefs();
  }

  ngOnDestroy() {
    this.stopFitbitPolling();
  }

  toggle(section: 'password' | 'email' | 'username' | 'identity' | 'tonnage' | 'ai' | 'language' | 'delete') {
    this.openSection.set(this.openSection() === section ? null : section);
    this.clearFeedback();
  }

  private clearFeedback() {
    this.successMessage.set('');
    this.errorMessage.set('');
  }

  onChangePassword() {
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage.set(this.i18n.t('settings.password.mismatch'));
      return;
    }
    this.isLoading.set(true);
    this.clearFeedback();
    this.chironApi.changePassword({ currentPassword: this.currentPasswordForPwd, newPassword: this.newPassword }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set(this.i18n.t('settings.password.updated'));
        this.currentPasswordForPwd = '';
        this.newPassword = '';
        this.confirmPassword = '';
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || this.i18n.t('settings.password.wrong'));
      }
    });
  }

  onChangeEmail() {
    this.isLoading.set(true);
    this.clearFeedback();
    this.chironApi.changeEmail({ newEmail: this.newEmail }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.currentEmail.set(this.newEmail);
        this.successMessage.set(this.i18n.t('settings.email.updated'));
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || this.i18n.t('settings.email.error'));
      }
    });
  }

  onChangeIdentity() {
    this.isLoading.set(true);
    this.clearFeedback();
    const prenom = this.newPrenom.trim() || null;
    const nom = this.newNom.trim() || null;
    this.chironApi.changeIdentity({ prenom, nom }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.currentPrenom.set(prenom);
        this.currentNom.set(nom);
        this.successMessage.set(this.i18n.t('settings.identity.updated'));
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || this.i18n.t('settings.identity.error'));
      }
    });
  }

  onChangeUsername() {
    this.isLoading.set(true);
    this.clearFeedback();
    this.chironApi.changeUsername({ newUsername: this.newUsername }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.authService.saveToken(res.token);
        this.username = this.newUsername;
        this.successMessage.set(this.i18n.t('settings.username.updated'));
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || this.i18n.t('settings.username.error'));
      }
    });
  }

  onDeleteAccount() {
    if (this.deleteConfirm !== this.username) {
      this.errorMessage.set(this.i18n.t('settings.delete.mismatch'));
      return;
    }
    this.isLoading.set(true);
    this.chironApi.deleteProfile(this.username).subscribe({
      next: () => {
        this.authService.logout();
      },
      error: () => {
        this.isLoading.set(false);
        this.errorMessage.set(this.i18n.t('settings.delete.error'));
      }
    });
  }

  // --- Profil sportif ---

  goToOnboarding() {
    this.router.navigate(['/onboarding']);
  }

  // --- Liaison Olympus ---

  loadNutritionStatus() {
    this.chironApi.getNutritionStatus().subscribe({
      next: (status) => this.nutritionStatus.set(status),
      error: () => this.nutritionStatus.set(null)
    });
  }

  linkOlympus() {
    const pseudo = this.olympusPseudo().trim();
    const password = this.olympusPassword();
    if (!pseudo || !password) {
      this.linkError.set(this.i18n.t('settings.olympus.credsRequired'));
      return;
    }
    this.isLinking.set(true);
    this.linkError.set(null);
    this.chironApi.linkOlympus(pseudo, password).subscribe({
      next: (status) => {
        this.nutritionStatus.set(status);
        this.olympusPseudo.set('');
        this.olympusPassword.set('');
        this.isLinking.set(false);
      },
      error: (err) => {
        this.isLinking.set(false);
        const msg = err?.error?.message ?? this.i18n.t('settings.olympus.linkFail');
        this.linkError.set(msg);
      }
    });
  }

  unlinkOlympus() {
    if (!confirm(this.i18n.t('settings.olympus.unlinkConfirm'))) return;
    this.chironApi.unlinkOlympus().subscribe({
      next: () => this.loadNutritionStatus(),
      error: () => alert(this.i18n.t('settings.olympus.unlinkError'))
    });
  }

  // --- Liaison Fitbit ---

  readonly natif = estNatif();

  exempterBatterie() {
    ChironCourse.exempterBatterie().catch(() => {});
  }

  loadFitbitStatus() {
    this.chironApi.getFitbitStatus().subscribe({
      next: (status) => this.fitbitStatus.set(status),
      error: () => this.fitbitStatus.set(null)
    });
  }

  connectFitbit() {
    this.fitbitError.set(null);
    this.isFitbitConnecting.set(true);
    this.chironApi.getFitbitAuthorizeUrl().subscribe({
      next: ({ authorizeUrl }) => {
        ouvrirDansUnOnglet(authorizeUrl);
        this.startFitbitPolling();
      },
      error: () => {
        this.isFitbitConnecting.set(false);
        this.fitbitError.set(this.i18n.t('settings.fitbit.startError'));
      }
    });
  }

  disconnectFitbit() {
    if (!confirm(this.i18n.t('settings.fitbit.disconnectConfirm'))) return;
    this.chironApi.unlinkFitbit().subscribe({
      next: () => this.loadFitbitStatus(),
      error: () => alert(this.i18n.t('settings.fitbit.disconnectError'))
    });
  }

  goToFitbitDashboard() {
    this.router.navigate(['/fitbit']);
  }

  private startFitbitPolling() {
    this.stopFitbitPolling();
    let elapsedSeconds = 0;
    this.fitbitPollHandle = setInterval(() => {
      elapsedSeconds += 3;
      this.chironApi.getFitbitStatus().subscribe({
        next: (status) => {
          if (status.linked) {
            this.fitbitStatus.set(status);
            this.isFitbitConnecting.set(false);
            this.stopFitbitPolling();
          }
        },
        error: () => {}
      });
      if (elapsedSeconds >= 180) {
        this.isFitbitConnecting.set(false);
        this.stopFitbitPolling();
      }
    }, 3000);
  }

  private stopFitbitPolling() {
    if (this.fitbitPollHandle != null) {
      clearInterval(this.fitbitPollHandle);
      this.fitbitPollHandle = null;
    }
  }

  // --- Import manuel d'un rapport Visbody (PDF) ---
  visbodyUploading = signal(false);
  visbodyMessage = signal<string | null>(null);
  visbodyError = signal(false);

  uploadVisbody(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.visbodyUploading.set(true);
    this.visbodyMessage.set(null);
    this.chironApi.uploadVisbodyPdf(file).subscribe({
      next: (res) => {
        this.visbodyUploading.set(false);
        this.visbodyError.set(res.outcome === 'INVALID' || res.outcome === 'USER_NOT_FOUND');
        this.visbodyMessage.set(res.detail);
        input.value = '';
      },
      error: (err) => {
        this.visbodyUploading.set(false);
        this.visbodyError.set(true);
        this.visbodyMessage.set(err?.error?.detail ?? this.i18n.t('settings.visbody.importError'));
        input.value = '';
      },
    });
  }

  // --- Import manuel d'un export Boditrax (CSV) ---
  boditraxUploading = signal(false);
  boditraxMessage = signal<string | null>(null);
  boditraxError = signal(false);

  uploadBoditrax(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.boditraxUploading.set(true);
    this.boditraxMessage.set(null);
    this.chironApi.uploadBoditraxCsv(file).subscribe({
      next: (res) => {
        this.boditraxUploading.set(false);
        this.boditraxError.set(res.outcome === 'INVALID' || res.outcome === 'USER_NOT_FOUND');
        this.boditraxMessage.set(res.detail);
        input.value = '';
      },
      error: (err) => {
        this.boditraxUploading.set(false);
        this.boditraxError.set(true);
        this.boditraxMessage.set(err?.error?.detail ?? this.i18n.t('settings.boditrax.importError'));
        input.value = '';
      },
    });
  }
}
