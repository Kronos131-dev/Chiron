import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import {
  GlauxApi,
  NoctuaBriefingDetailDto,
  NoctuaBriefingDto,
  NoctuaBriefingType,
} from '../../service/glaux-api';
import { NoctuaNotifications } from '../../service/noctua-notifications';
import { I18nService } from '../../service/i18n.service';

@Component({
  selector: 'app-noctua',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, TranslatePipe],
  templateUrl: './noctua.html',
  styleUrl: './noctua.css',
})
export class Noctua implements OnInit {
  private api = inject(GlauxApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private notifications = inject(NoctuaNotifications);
  private i18n = inject(I18nService);

  private readonly typeIcons: Record<NoctuaBriefingType, string> = {
    REVEIL: 'wb_sunny',
    ACTIVITE: 'directions_run',
    COUCHER: 'bedtime',
  };

  private readonly typeLabels: Record<NoctuaBriefingType, string> = {
    REVEIL: 'noctua.typeReveil',
    ACTIVITE: 'noctua.typeActivite',
    COUCHER: 'noctua.typeCoucher',
  };

  selectedId = signal<number | null>(null);

  briefings = signal<NoctuaBriefingDto[]>([]);
  loading = signal(true);

  detail = signal<NoctuaBriefingDetailDto | null>(null);
  loadingDetail = signal(false);

  userInput = signal('');
  sending = signal(false);
  sendError = signal(false);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.selectedId.set(Number(idParam));
      this.loadDetail(Number(idParam));
    } else {
      this.loadList();
    }
  }

  private loadList(): void {
    this.loading.set(true);
    this.api.getNoctuaBriefings(14).subscribe({
      next: (b) => {
        this.briefings.set(b);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadDetail(id: number): void {
    this.loadingDetail.set(true);
    this.api.getNoctuaBriefing(id).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loadingDetail.set(false);
      },
      error: () => this.loadingDetail.set(false),
    });
    this.api.marquerBriefingLu(id).subscribe({
      next: () => this.notifications.rafraichir(),
      error: () => {},
    });
  }

  ouvrir(id: number): void {
    this.router.navigate(['/noctua', id]);
  }

  retour(): void {
    this.router.navigate(['/noctua']);
  }

  envoyer(): void {
    const message = this.userInput().trim();
    const id = this.selectedId();
    if (!message || id == null) return;

    this.userInput.set('');
    this.sending.set(true);
    this.sendError.set(false);

    const current = this.detail();
    if (current) {
      this.detail.set({
        ...current,
        messages: [...current.messages, { role: 'USER', content: message }],
      });
    }

    this.api.envoyerMessageNoctua(id, message, this.i18n.lang()).subscribe({
      next: (res) => {
        const updated = this.detail();
        if (updated) {
          this.detail.set({
            ...updated,
            messages: [...updated.messages, { role: 'AI', content: res.reply }],
          });
        }
        this.sending.set(false);
      },
      error: () => {
        this.sending.set(false);
        this.sendError.set(true);
      },
    });
  }

  iconFor(type: NoctuaBriefingType): string {
    return this.typeIcons[type] ?? 'nights_stay';
  }

  labelFor(type: NoctuaBriefingType): string {
    return this.typeLabels[type] ?? 'noctua.typeReveil';
  }

  dateLabel(iso: string): string {
    return iso.slice(8, 10) + '/' + iso.slice(5, 7);
  }

  heureLabel(iso: string): string {
    return new Date(iso).toLocaleTimeString(this.i18n.lang() === 'en' ? 'en' : 'fr', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
