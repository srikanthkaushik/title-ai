import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TransferService } from './transfer.service';
import { HistoryEntry, TransferRequest, TransferResponse, SupervisorDecision } from './transfer.model';

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
  readonly pendingThreadId = signal<string | null>(null);
  readonly deciding = signal(false);
  readonly supervisorNote = signal<string>('');
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
    this.phase.set('Analyzing…');
    this.streamingText.set('');
    this.notes.set('');
    this.pendingThreadId.set(null);
    this.supervisorNote.set('');
    this.activeHistoryId.set(null);

    const request: TransferRequest = {
      question: this.question,
      ...(this.vehicleVin.trim() && { vehicleVin: this.vehicleVin.trim() }),
      ...(this.originState       && { originState: this.originState }),
      ...(this.county            && { county: this.county }),
      ...(this.transferType      && { transferType: this.transferType }),
    };

    this.transferService.query(request).subscribe({
      next: (agentResponse) => {
        this.response.set(agentResponse.response);
        this.phase.set('');
        this.pendingThreadId.set(agentResponse.awaitingSupervisorDecision ? agentResponse.threadId : null);
        const id = ++this.historyCounter;
        this.activeHistoryId.set(id);
        this.history.update(h => [{
          id,
          timestamp: new Date(),
          question: this.question,
          response: agentResponse.response,
          errorCode: null,
          errorMessage: null,
          notes: '',
          threadId: agentResponse.threadId,
          awaitingSupervisorDecision: agentResponse.awaitingSupervisorDecision
        }, ...h]);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as { error?: string; message?: string; detail?: string } | null;
        this.errorCode.set(body?.error ?? 'CONNECTION_ERROR');
        this.errorMessage.set(body?.message ?? body?.detail ?? (err.message || 'Request failed'));
        this.phase.set('');
        const id = ++this.historyCounter;
        this.activeHistoryId.set(id);
        this.history.update(h => [{
          id,
          timestamp: new Date(),
          question: this.question,
          response: null,
          errorCode: this.errorCode(),
          errorMessage: this.errorMessage(),
          notes: '',
          threadId: null,
          awaitingSupervisorDecision: false
        }, ...h]);
        this.loading.set(false);
      }
    });
  }

  // Supervisor approves or denies a paused referral. Resumes the LangGraph4j checkpoint
  // identified by pendingThreadId — this does not re-invoke the LLM, it merges the
  // decision into graph state and lets the run fall through to END.
  decide(decision: SupervisorDecision): void {
    const threadId = this.pendingThreadId();
    if (!threadId) return;

    this.deciding.set(true);
    this.transferService.resume({
      threadId,
      decision,
      ...(this.supervisorNote().trim() && { note: this.supervisorNote().trim() })
    }).subscribe({
      next: (agentResponse) => {
        this.response.set(agentResponse.response);
        this.pendingThreadId.set(null);
        this.deciding.set(false);
        const id = this.activeHistoryId();
        if (id !== null) {
          this.history.update(h => h.map(e => e.id === id
            ? { ...e, response: agentResponse.response, awaitingSupervisorDecision: false }
            : e));
        }
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as { error?: string; message?: string; detail?: string } | null;
        this.errorCode.set(body?.error ?? 'CONNECTION_ERROR');
        this.errorMessage.set(body?.message ?? body?.detail ?? (err.message || 'Resume failed'));
        this.deciding.set(false);
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
    this.pendingThreadId.set(entry.awaitingSupervisorDecision ? entry.threadId : null);
    this.supervisorNote.set('');
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
