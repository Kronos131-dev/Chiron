import { Signal, computed, signal } from '@angular/core';
import { CoursePointDto, CourseSplitDto } from './chiron-api';
import { EtatCourse, mesurer } from './course-tracker';
import { ChironCourse, EtatNatif } from './chiron-course.plugin';
import { CourseRuntime, OptionsCourse } from './course-runtime';
import { Commande, interpreter } from '../util/commandes-vocales';

const MS_PAR_SECONDE = 1000;

const ETAT_INITIAL: EtatNatif = {
  demarree: false,
  enPause: false,
  distanceM: 0,
  dureeMs: 0,
  allureCouranteKmh: 0,
  allureMoyenneKmh: 0,
  nbPoints: 0,
  kilometres: 0,
  precisionM: null,
  erreurGps: null,
  signalPerdu: false,
  ecoute: false,
  cibleMinParKm: null,
};

export class RuntimeNatif implements CourseRuntime {
  readonly natif = true;

  private readonly natifEtat = signal<EtatNatif>(ETAT_INITIAL);
  private readonly termine = signal(false);

  readonly points = signal<CoursePointDto[]>([]);
  readonly transcript = signal('');
  readonly commandeComprise = signal<boolean | null>(null);
  readonly audioActif = signal(true);
  readonly microDisponible = signal(true);

  readonly etat: Signal<EtatCourse> = computed(() => {
    if (this.termine()) return 'termine';
    const etat = this.natifEtat();
    if (!etat.demarree) return 'pret';
    return etat.enPause ? 'enPause' : 'enCours';
  });

  // WHY: la mesure vivante vient du service natif, mais les splits se recalculent ici avec
  // l'algorithme web sur les points reçus au fil de l'eau. Personne ne les lit écran
  // verrouillé, et cela évite une quatrième implémentation du découpage kilométrique.
  readonly splits: Signal<CourseSplitDto[]> = computed(() => mesurer(this.points()).splits);

  readonly distanceM = computed(() => this.natifEtat().distanceM);
  readonly dureeS = computed(() => Math.floor(this.natifEtat().dureeMs / MS_PAR_SECONDE));
  readonly allureCouranteKmh = computed(() => this.natifEtat().allureCouranteKmh);
  readonly allureMoyenneKmh = computed(() => this.natifEtat().allureMoyenneKmh);
  readonly precisionM = computed(() => this.natifEtat().precisionM);
  readonly erreurGps = computed(() => this.natifEtat().erreurGps);
  readonly signalPerdu = computed(() => this.natifEtat().signalPerdu);
  readonly ecoute = computed(() => this.natifEtat().ecoute);

  private options: OptionsCourse | null = null;
  private abonnements: { remove: () => Promise<void> }[] = [];

  attacher(): void {
    this.brancherLesEvenements();
  }

  private async brancherLesEvenements(): Promise<void> {
    if (this.abonnements.length) return;
    this.abonnements.push(
      await ChironCourse.addListener('etat', (etat) => this.recevoirEtat(etat)),
      await ChironCourse.addListener('commande', (donnees) => this.recevoirCommande(donnees)),
      await ChironCourse.addListener('echecEcoute', () => this.commandeComprise.set(false)),
      await ChironCourse.addListener('casque', () => this.rafraichir()),
    );
  }

  private recevoirEtat(etat: EtatNatif): void {
    const nouveaux = etat.nouveauxPoints ?? [];
    if (nouveaux.length) this.points.update((points) => [...points, ...nouveaux]);
    this.natifEtat.set({ ...etat, nouveauxPoints: undefined });
  }

  private recevoirCommande(donnees: { texte: string; definitif: boolean }): void {
    this.transcript.set(donnees.texte);
    if (!donnees.definitif) return;
    const commande = interpreter(donnees.texte);
    if (!commande) {
      this.commandeComprise.set(false);
      this.dire(this.phrase('notUnderstood'), true);
      return;
    }
    this.commandeComprise.set(true);
    this.executer(commande);
  }

  // WHY: le service Android survit à la page. Retrouver une course déjà en route est donc une
  // question posée au natif, pas une relecture de localStorage — c'est ce qui permet de
  // rouvrir l'app en pleine sortie et de retomber sur les vrais chiffres.
  async reprendreCourseEnCours(): Promise<boolean> {
    await this.brancherLesEvenements();
    const etat = await ChironCourse.etat();
    this.natifEtat.set({ ...ETAT_INITIAL, ...etat });
    if (!etat.demarree) return false;
    const { points } = await ChironCourse.points();
    this.points.set(points ?? []);
    return true;
  }

  cibleRetenue(): number | null {
    return this.natifEtat().cibleMinParKm;
  }

  configurer(options: OptionsCourse): void {
    this.options = options;
    if (!this.natifEtat().demarree) return;
    ChironCourse.configurer({
      titre: options.titre,
      phrases: options.phrases,
      appuiCourt: options.appuiCourt,
      appuiLong: options.appuiLong,
    }).catch(() => {});
  }

  async demarrer(): Promise<void> {
    const options = this.options;
    if (!options) return;
    await this.brancherLesEvenements();
    await ChironCourse.demanderPermissions();
    if (this.natifEtat().demarree) return;
    this.points.set([]);
    this.termine.set(false);
    await ChironCourse.demarrer({
      cibleMinParKm: options.cibleMinParKm,
      langue: options.langue,
      titre: options.titre,
      phrases: options.phrases,
      appuiCourt: options.appuiCourt,
      appuiLong: options.appuiLong,
    });
  }

  basculerPause(): void {
    ChironCourse.basculerPause()
      .then((etat) => this.natifEtat.set({ ...this.natifEtat(), ...etat }))
      .catch(() => {});
  }

  async arreter(): Promise<void> {
    try {
      const resultat = await ChironCourse.arreter();
      if (resultat.points?.length) this.points.set(resultat.points);
      this.natifEtat.set({ ...this.natifEtat(), ...resultat, nouveauxPoints: undefined });
    } catch {}
    this.termine.set(true);
  }

  fixerCible(minParKm: number | null): void {
    ChironCourse.fixerCible({ cibleMinParKm: minParKm })
      .then((etat) => this.natifEtat.set({ ...this.natifEtat(), ...etat }))
      .catch(() => {});
  }

  dire(texte: string, prioritaire: boolean): void {
    if (!texte) return;
    ChironCourse.annoncer({ texte, prioritaire }).catch(() => {});
  }

  commencerEcoute(): void {
    ChironCourse.ecouter().catch(() => {});
  }

  // WHY: le SpeechRecognizer d'Android se referme seul au silence ou au bout de son délai.
  // Relâcher le bouton ne doit donc rien couper, sinon l'écoute meurt avant le premier mot.
  terminerEcoute(): void {}

  executer(commande: Commande): void {
    const etat = this.natifEtat();
    switch (commande.nom) {
      case 'pause':
        if (!etat.enPause) this.basculerPause();
        return;
      case 'reprendre':
        if (etat.enPause) this.basculerPause();
        return;
      case 'cible':
        if (commande.cibleMinParKm !== undefined) this.fixerCible(commande.cibleMinParKm);
        return;
      default:
        ChironCourse.executerAction({ action: commande.nom })
          .then((suivant) => this.natifEtat.set({ ...this.natifEtat(), ...suivant }))
          .catch(() => {});
    }
  }

  purger(): void {}

  liberer(): void {
    for (const abonnement of this.abonnements) abonnement.remove().catch(() => {});
    this.abonnements = [];
  }

  private rafraichir(): void {
    ChironCourse.etat()
      .then((etat) => this.natifEtat.set({ ...this.natifEtat(), ...etat }))
      .catch(() => {});
  }

  private phrase(cle: string): string {
    return this.options?.phrases[cle] ?? '';
  }
}
