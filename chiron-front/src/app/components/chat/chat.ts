import { Component, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ChironApi, ConversationSummary } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { I18nService } from '../../service/i18n.service';
import { HeaderComponent } from '../shared/header/header';
import { MarkdownPipe } from './markdown.pipe';
import { TranslatePipe } from '../../service/translate.pipe';
import { ReconnaissanceVocale, creerReconnaissance } from '../../service/reconnaissance-vocale';
import { corrigerVocabulaire } from '../../util/vocabulaire-vocal';

/**
 * Interface defining the structure of a chat message.
 */
export interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
}

/**
 * Main AI Chat component (Chiron Interface).
 * Handles voice and text interactions, session management, and UI logic.
 */
@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, CommonModule, HeaderComponent, MarkdownPipe, TranslatePipe],
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

  /** Conversations passées de l'utilisateur (menu d'historique). */
  conversations = signal<ConversationSummary[]>([]);

  /** Id de la conversation active, ou null pour une nouvelle conversation. */
  activeConversationId = signal<number | null>(null);

  /** Ouverture du panneau d'historique des conversations. */
  showHistory = signal(false);

  /** Reconnaissance vocale, web ou native selon la plateforme. */
  recognition: ReconnaissanceVocale = creerReconnaissance();

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
    private router: Router,
    public i18n: I18nService
  ) {}

  /**
   * Lifecycle hook to initialize the component.
   * Fetches the user's profile to determine admin rights.
   */
  ngOnInit() {

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

    this.refreshConversations();
  }

  /** Recharge la liste des conversations pour le menu d'historique. */
  private refreshConversations() {
    this.chironApi.listConversations().subscribe({
      next: (list) => this.conversations.set(list),
      error: () => { /* silencieux : le chat reste utilisable sans l'historique */ }
    });
  }

  /** Ouvre/ferme le panneau d'historique. */
  toggleHistory() {
    this.showHistory.update(v => !v);
  }

  /** Démarre une nouvelle conversation (vide l'écran, oublie l'id actif). */
  newConversation() {
    this.messages.set([]);
    this.activeConversationId.set(null);
    this.showHistory.set(false);
  }

  /** Recharge une conversation existante et l'affiche. */
  loadConversation(id: number) {
    this.chironApi.getConversationMessages(id).subscribe({
      next: (msgs) => {
        this.messages.set(msgs.map(m => ({
          role: m.role === 'AI' ? 'ai' : 'user',
          content: m.content
        })));
        this.activeConversationId.set(id);
        this.showHistory.set(false);
      },
      error: (err) => console.error(err)
    });
  }

  /** Supprime une conversation ; si c'était l'active, repart sur une nouvelle. */
  deleteConversation(id: number, event: Event) {
    event.stopPropagation();
    this.chironApi.deleteConversation(id).subscribe({
      next: () => {
        if (this.activeConversationId() === id) {
          this.newConversation();
        }
        this.refreshConversations();
      },
      error: (err) => console.error(err)
    });
  }

  /**
   * Toggles the voice recording state to capture user speech.
   */
  toggleRecording() {
    if (!this.recognition.disponible()) {
      alert(this.i18n.t('chat.noSpeech'));
      return;
    }

    if (this.isRecording()) {
      this.recognition.arreter();
      this.isRecording.set(false);
      return;
    }

    this.isRecording.set(true);
    this.recognition.demarrer(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR', {
      final: (texte) => {
        this.isRecording.set(false);
        this.userInput.set(corrigerVocabulaire(texte));
        this.onSend();
      },
      erreur: (raison) => {
        this.isRecording.set(false);
        this.addMessage('ai', this.i18n.t('chat.micError', { error: raison }));
      },
    });
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

    // Un réessai silencieux avant d'afficher une erreur : absorbe les ratés transitoires du modèle.
    this.dispatchMessage(message, true);
  }

  /**
   * Envoie le message au coach. En cas d'échec, retente une fois en silence (retryOnError)
   * avant de signaler que le temple est inaccessible.
   */
  private dispatchMessage(message: string, retryOnError: boolean) {
    this.chironApi.sendMessage(message, this.activeConversationId(), this.i18n.lang()).subscribe({
      next: (res) => {
        this.activeConversationId.set(res.conversationId);
        this.addMessage('ai', res.reply);
        this.isLoading.set(false);
        this.refreshConversations();
      },
      error: (err) => {
        if (retryOnError) {
          setTimeout(() => this.dispatchMessage(message, false), 800);
          return;
        }
        console.error(err);
        this.addMessage('ai', this.i18n.t('chat.error'));
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Appends a new message to the component's internal chat history signal.
   * AI responses are stored as raw Markdown and rendered (and sanitized) by the
   * {@link MarkdownPipe} at display time; user messages stay plain text.
   *
   * @param role    The origin of the message ('user' or 'ai').
   * @param content The text content of the message.
   */
  private addMessage(role: 'user' | 'ai', content: string) {
    this.messages.update(anciensMessages => [...anciensMessages, { role, content }]);
  }
}
