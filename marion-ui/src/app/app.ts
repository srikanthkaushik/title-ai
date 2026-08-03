import { Component, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TransferService } from './transfer.service';
import { HistoryEntry, TransferRequest, TransferResponse, SupervisorDecision } from './transfer.model';
import { SupervisorQueue } from './supervisor-queue';

@Component({
  selector: 'app-root',
  imports: [FormsModule, DecimalPipe, DatePipe, SupervisorQueue],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnDestroy {
  readonly originStates = ['Verdana', 'Crestwood', 'Halloway', 'Pembrook'];
  readonly counties = [
    'Marion County', 'Riverside County', 'Capital County',
    'Jefferson County', 'Franklin County'
  ];
  readonly transferTypes = ['PURCHASE', 'RELOCATION'];

  // Examiner tab drives the query form + local history; Supervisor Queue tab reads
  // /pending-referrals directly, so it works from an independent browser session.
  readonly view = signal<'examiner' | 'supervisor'>('examiner');

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
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private static readonly POLL_MS = 4000;

  constructor(private transferService: TransferService) {}

  ngOnDestroy(): void {
    this.stopPolling();
  }

  // Origin State drives a real MCP tool call (lookupTaxReciprocity) — it isn't decorative, so
  // typing the state in the Scenario text alone doesn't reach the agent. Auto-fill it from text
  // so examiners don't have to state it twice; never override an explicit manual selection.
  detectOriginState(text: string): void {
    if (this.originState) return;
    const match = this.originStates.find(s => new RegExp(`\\b${s}\\b`, 'i').test(text));
    if (match) {
      this.originState = match;
    }
  }

  submit(): void {
    if (!this.question.trim()) return;

    this.stopPolling();
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
        if (agentResponse.awaitingSupervisorDecision) {
          this.startPolling(agentResponse.threadId);
        }
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
        this.stopPolling();
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
    this.stopPolling();
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
    if (entry.awaitingSupervisorDecision && entry.threadId) {
      this.startPolling(entry.threadId);
    }
  }

  // Detects a resume that happened from a different session (e.g. the Supervisor Queue) while
  // this tab is still showing "Awaiting Supervisor Decision" — without this, the originating
  // tab has no signal that the run finished and is stuck showing a stale pending card forever.
  private startPolling(threadId: string): void {
    this.stopPolling();
    this.pollHandle = setInterval(() => {
      this.transferService.status(threadId).subscribe({
        next: (agentResponse) => {
          if (agentResponse.awaitingSupervisorDecision) return;
          this.stopPolling();
          this.response.set(agentResponse.response);
          this.pendingThreadId.set(null);
          const id = this.activeHistoryId();
          if (id !== null) {
            this.history.update(h => h.map(e => e.id === id
              ? { ...e, response: agentResponse.response, awaitingSupervisorDecision: false }
              : e));
          }
        },
        error: () => { /* transient poll failure — try again next tick */ }
      });
    }, App.POLL_MS);
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
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
