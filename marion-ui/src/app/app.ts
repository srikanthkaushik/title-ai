import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { TransferService } from './transfer.service';
import { HistoryEntry, TransferRequest, TransferResponse } from './transfer.model';

@Component({
  selector: 'app-root',
  imports: [FormsModule, DecimalPipe, DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly originStates = ['Verdana', 'Crestwood', 'Halloway', 'Pembrook'];
  readonly counties = [
    'Marion County', 'Riverside County', 'Capital County',
    'Jefferson County', 'Franklin County'
  ];
  readonly transferTypes = ['PURCHASE', 'RELOCATION'];

  // plain properties — [(ngModel)] drives these
  question = '';
  vehicleVin = '';
  originState = '';
  county = '';
  transferType = '';

  // signals — mutations trigger change detection in zoneless mode
  readonly loading = signal(false);
  readonly showReasoning = signal(false);
  readonly response = signal<TransferResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly errorCode = signal<string | null>(null);
  readonly history = signal<HistoryEntry[]>([]);
  readonly phase = signal<string>('');
  readonly streamingText = signal<string>('');
  readonly copied = signal(false);
  readonly notes = signal<string>('');
  private activeHistoryId = signal<number | null>(null);
  private historyCounter = 0;

  constructor(private transferService: TransferService) {}

  submit(): void {
    if (!this.question.trim()) return;

    this.loading.set(true);
    this.response.set(null);
    this.errorMessage.set(null);
    this.errorCode.set(null);
    this.showReasoning.set(false);
    this.phase.set('');
    this.streamingText.set('');
    this.notes.set('');
    this.activeHistoryId.set(null);

    const request: TransferRequest = {
      question: this.question,
      ...(this.vehicleVin.trim() && { vehicleVin: this.vehicleVin.trim() }),
      ...(this.originState       && { originState: this.originState }),
      ...(this.county            && { county: this.county }),
      ...(this.transferType      && { transferType: this.transferType }),
    };

    this.transferService.stream(request).subscribe({
      next: (event) => {
        if (event.type === 'phase') {
          this.phase.set(event.message);
        } else if (event.type === 'token') {
          this.streamingText.update(t => t + event.text);
        } else if (event.type === 'result') {
          this.response.set(event.data);
          this.phase.set('');
          this.streamingText.set('');
          const id = ++this.historyCounter;
          this.activeHistoryId.set(id);
          this.history.update(h => [{
            id,
            timestamp: new Date(),
            question: this.question,
            response: event.data,
            errorCode: null,
            errorMessage: null,
            notes: ''
          }, ...h]);
          this.loading.set(false);
        } else if (event.type === 'error') {
          this.errorMessage.set(event.message);
          this.errorCode.set('STREAM_ERROR');
          this.phase.set('');
          this.streamingText.set('');
          const id = ++this.historyCounter;
          this.activeHistoryId.set(id);
          this.history.update(h => [{
            id,
            timestamp: new Date(),
            question: this.question,
            response: null,
            errorCode: 'STREAM_ERROR',
            errorMessage: event.message,
            notes: ''
          }, ...h]);
          this.loading.set(false);
        }
      },
      error: (err) => {
        const message = (err as Error).message ?? 'Stream connection failed';
        this.errorMessage.set(message);
        this.errorCode.set('CONNECTION_ERROR');
        this.phase.set('');
        this.streamingText.set('');
        const id = ++this.historyCounter;
        this.activeHistoryId.set(id);
        this.history.update(h => [{
          id,
          timestamp: new Date(),
          question: this.question,
          response: null,
          errorCode: 'CONNECTION_ERROR',
          errorMessage: message,
          notes: ''
        }, ...h]);
        this.loading.set(false);
      },
      complete: () => {
        this.loading.set(false);
      }
    });
  }

  selectHistory(entry: HistoryEntry): void {
    this.response.set(entry.response);
    this.errorMessage.set(entry.errorMessage);
    this.errorCode.set(entry.errorCode);
    this.showReasoning.set(false);
    this.phase.set('');
    this.streamingText.set('');
    this.notes.set(entry.notes);
    this.activeHistoryId.set(entry.id);
  }

  saveNotes(): void {
    const id = this.activeHistoryId();
    if (id === null) return;
    const text = this.notes();
    this.history.update(h => h.map(e => e.id === id ? { ...e, notes: text } : e));
  }

  copyChecklist(items: string[]): void {
    const text = items.map((item, i) => `${i + 1}. ${item}`).join('\n');
    navigator.clipboard.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  printPage(): void {
    window.print();
  }

  get feeEntries(): { label: string; value: number }[] {
    const res = this.response();
    if (!res?.fees) return [];
    const labels: Record<string, string> = {
      titleFee: 'Title Fee',
      vinFee: 'VIN Inspection Fee',
      registrationFee: 'Registration Fee',
      emissionsFee: 'Emissions Fee',
      lienReleaseFee: 'Lien Release Fee',
    };
    return Object.entries(res.fees)
      .filter(([k, v]) => k !== 'totalToDMV' && (v as number) > 0)
      .map(([k, v]) => ({ label: labels[k] ?? k, value: v as number }));
  }
}
