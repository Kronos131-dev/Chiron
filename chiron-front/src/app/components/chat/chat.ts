import { Component, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';

/**
 * Interface defining the structure of a chat message.
 */
export interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
}

declare var webkitSpeechRecognition: any;
declare var SpeechRecognition: any;

/**
 * Présentation préfaite (texte brut) affichée quand l'utilisateur clique sur
 * « Présente-moi l'application ». Volontairement NON envoyée à l'IA : elle décrit
 * l'app dans sa globalité sans déclencher d'outils ni d'appel réseau. Le chat
 * affiche du texte brut (CSS `white-space: pre-line`), donc pas de markdown ici.
 */
const APP_PRESENTATION = `Bienvenue dans Chiron ⚔️

Chiron est ton sanctuaire d'entraînement : un coach IA doublé d'une suite d'outils pour progresser. Voici tout ce que tu peux faire.

🗣️ Le Sanctuaire (ce chat) — parle-moi à la voix ou au clavier pour enregistrer tes séances, poser des questions et obtenir des conseils. Je comprends le langage naturel.

📖 Les Annales — l'historique de toutes tes séances réalisées (séries, reps, charges), semaine par semaine.

📋 Les Programmes — crée et réutilise des modèles de séances, avec supersets et bisets.

🏋️ La Séance — ton interface d'entraînement en direct : ajoute tes exercices, log tes séries et enregistre la séance.

💎 Le Trésor — tes records personnels (1RM, PR) et ton poids de corps, classés par rangs et paliers de force.

🏛️ L'Agora — l'espace social : découvre les autres athlètes, leurs rangs et leurs profils.

📊 Statistiques — tes graphes de progression : force, volume, répartition musculaire, poids, nutrition et récupération.

❤️ Données Fitbit — ton tableau de bord santé : sommeil, fréquence cardiaque au repos, pas…

🍽️ Nutrition (Olympus) — lie ton compte Olympus dans les Réglages pour suivre calories, macros et poids de corps.

👥 Coaching — désigne un coach, ou entraîne tes élèves et accède à leurs programmes.

👤 Profil & Réglages — gère tes infos, ta photo, ta confidentialité et tes liaisons.

Prêt ? Dis-moi par exemple : « J'ai fait 4x8 au développé couché à 80 kg ».`;

/**
 * Main AI Chat component (Chiron Interface).
 * Handles voice and text interactions, session management, and UI logic.
 */
@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, CommonModule, HeaderComponent],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat implements OnInit {
  /** Signal holding the current user input text. */
  userInput = signal('');

  /** Signal indicating whether the AI is currently processing a response. */
  isLoading = signal(false);

  /** Signal indicating whether voice recording is active. */
  isRecording = signal(false);

  /** Signal holding the array of messages in the conversation history. */
  messages = signal<ChatMessage[]>([]);

  /** Web Speech API recognition instance. */
  recognition: any;

  /** The username of the currently authenticated user. */
  currentUsername: string = '';

  /**
   * Initializes a new instance of the Chat component.
   *
   * @param chironApi   Service for communicating with the backend AI API.
   * @param authService Service handling user authentication and token state.
   * @param router      Angular router for navigation.
   */
  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router
  ) {}

  /**
   * Lifecycle hook to initialize the component.
   * Sets up speech recognition and fetches the user's profile to determine admin rights.
   */
  ngOnInit() {
    this.initSpeechRecognition();

    this.currentUsername = this.authService.getUsername() || 'Guerrier';

    // Redirige vers l'onboarding si le profil n'a jamais été complété.
    this.chironApi.getProfileSetup().subscribe({
      next: (setup) => {
        if (!setup.isOnboarded) {
          this.router.navigate(['/onboarding']);
        }
      },
      error: () => { /* silencieux : si l'endpoint échoue, on laisse le chat fonctionner */ }
    });
  }

  /**
   * Initializes the Web Speech API recognition engine with specific grammar replacements.
   */
  initSpeechRecognition() {
    // @ts-ignore
    const SpeechRecognitionAPI = window['SpeechRecognition'] || window['webkitSpeechRecognition'];

    if (SpeechRecognitionAPI) {
      this.recognition = new SpeechRecognitionAPI();
      this.recognition.lang = 'fr-FR';
      this.recognition.continuous = false;
      this.recognition.interimResults = false;

      this.recognition.onresult = (event: any) => {
        let transcript = event.results[0][0].transcript.toLowerCase();

        transcript = transcript
          .replace(/\b(crêpe|crêpes|crepe|crepes|rêve|rêves|bref|brefs)\b/g, 'reps')
          .replace(/\b(pêche|pèche|peche|puch|bouche|touche)\b/g, 'push')
          .replace(/\b(poule|poules|pull|pool)\b/g, 'pull')
          .replace(/\b(lex|l'ex|l'est|lait)\b/g, 'legs')
          .replace(/\b(chest press|juste presse|geste presse|chaise presse)\b/g, 'chest press')
          .replace(/\b(bench|bench press|banche|bain tu presses|bain de presse)\b/g, 'développé couché')
          .replace(/\b(incline bench|incline|un clean|un clin)\b/g, 'développé incliné')
          .replace(/\b(pec deck|pack deck|bec dec|pique nique)\b/g, 'pec deck')
          .replace(/\b(dips|chips|dix|gips)\b/g, 'dips')
          .replace(/\b(deadlift|dès de lift|tête lift)\b/g, 'soulevé de terre')
          .replace(/\b(rowing|héroïne|ruine|robin)\b/g, 'rowing')
          .replace(/\b(lat pulldown|la poule donne|la poule down)\b/g, 'tirage vertical')
          .replace(/\b(pull up|pull ups|poule up|poulpe)\b/g, 'tractions')
          .replace(/\b(squat|squatt|scoot|scot)\b/g, 'squat')
          .replace(/\b(leg press|l'express|l'ex presse|lait presse)\b/g, 'presse à cuisses')
          .replace(/\b(leg extension|l'ex tension|les extensions)\b/g, 'leg extension')
          .replace(/\b(leg curl|le girl|les girls)\b/g, 'leg curl')
          .replace(/\b(ohp|o a h p|eau h p)\b/g, 'développé militaire')
          .replace(/\b(élévations latérales|la téral|latérales)\b/g, 'élévations latérales')
          .replace(/\b(curl|coeur le|girl|gueule)\b/g, 'curl')
          .replace(/\b(triceps|triceps|tricepse)\b/g, 'triceps');

        this.userInput.set(transcript);
        this.onSend();
      };

      this.recognition.onend = () => {
        this.isRecording.set(false);
      };

      this.recognition.onerror = (event: any) => {
        console.error("Erreur micro:", event.error);
        this.isRecording.set(false);
        this.addMessage('ai', "Erreur : Le navigateur a bloqué le micro (" + event.error + ").");
      };
    } else {
      console.error("La reconnaissance vocale n'est pas supportée.");
    }
  }

  /**
   * Toggles the voice recording state to capture user speech.
   */
  toggleRecording() {
    if (!this.recognition) {
      alert("⚠️ Ton navigateur ne supporte pas la Web Speech API, ou l'accès est bloqué.");
      return;
    }

    if (this.isRecording()) {
      this.recognition.stop();
      this.isRecording.set(false);
    } else {
      this.recognition.start();
      this.isRecording.set(true);
    }
  }

  /**
   * Affiche la présentation préfaite de l'application sans passer par l'IA :
   * ajoute une bulle utilisateur « Présente-moi l'application » puis la réponse
   * écrite en dur. Aucun appel réseau, aucun outil déclenché.
   */
  presentApp() {
    if (this.isLoading()) return;
    this.messages.update(prev => [
      ...prev,
      { role: 'user', content: 'Présente-moi l\'application' },
      { role: 'ai', content: APP_PRESENTATION },
    ]);
  }

  /**
   * Sends the current user input text to the AI backend and awaits a response.
   */
  onSend() {
    const message = this.userInput().trim();
    if (!message || !this.currentUsername) return;

    this.addMessage('user', message);
    this.userInput.set('');
    this.isLoading.set(true);

    this.chironApi.sendMessage(this.currentUsername, message).subscribe({
      next: (res: any) => {
        this.addMessage('ai', res.toString());
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.addMessage('ai', "Erreur : Le temple est inaccessible.");
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Appends a new message to the component's internal chat history signal.
   *
   * @param role    The origin of the message ('user' or 'ai').
   * @param content The text content of the message.
   */
  private addMessage(role: 'user' | 'ai', content: string) {
    const clean = role === 'ai' ? this.stripMarkdown(content) : content;
    this.messages.update(anciensMessages => [...anciensMessages, { role, content: clean }]);
  }

  /**
   * Strips Markdown markers from an AI response. Chiron is instructed not to emit
   * Markdown, but the model occasionally does anyway — this removes the leftover
   * markers (**bold**, *italic*, headings, backticks, bullets) so the chat bubble
   * stays clean plain text.
   *
   * @param text The raw AI response.
   * @return The same text without Markdown formatting.
   */
  private stripMarkdown(text: string): string {
    return text
      .replace(/\*\*(.*?)\*\*/g, '$1')       // **gras**
      .replace(/__(.*?)__/g, '$1')           // __gras__
      .replace(/\*([^*\n]+)\*/g, '$1')       // *italique*
      .replace(/`+([^`]*)`+/g, '$1')         // `code`
      .replace(/^\s{0,3}#{1,6}\s+/gm, '')    // # titres
      .replace(/^\s*[-*+]\s+/gm, '• ')       // puces -, *, + → •
      .trim();
  }
}
