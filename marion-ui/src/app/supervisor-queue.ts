import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TransferService } from './transfer.service';
import { PendingReferral, SupervisorDecision } from './transfer.model';

interface QueueRow extends PendingReferral {
  note: string;
}

// Server-side supervisor queue — reads /pending-referrals directly, so it works from any
// browser session, not just the one that submitted the original scenario. This is what makes
// the HITL demo credible: open the Examiner tab in one window and this queue in another to
// show a real handoff between two roles, not just a UI element toggling in the same session.
@Component({
  selector: 'app-supervisor-queue',
  templateUrl: './supervisor-queue.html'
})
export class SupervisorQueue implements OnInit, OnDestroy {
  private static readonly POLL_MS = 4000;

  readonly rows = signal<QueueRow[]>([]);
  readonly loading = signal(false);
  readonly decidingId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefreshed = signal<Date | null>(null);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor(private transferService: TransferService) {}

  ngOnInit(): void {
    this.refresh();
    this.pollHandle = setInterval(() => this.refresh(), SupervisorQueue.POLL_MS);
  }

  ngOnDestroy(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
    }
  }

  refresh(): void {
    this.loading.set(true);
    this.transferService.pendingReferrals().subscribe({
      next: (referrals) => {
        // Preserve in-progress notes for threads still pending; drop notes for threads
        // that resolved (elsewhere, or via this same queue) since the last poll.
        const existingNotes = new Map(this.rows().map(r => [r.threadId, r.note]));
        this.rows.set(referrals.map(r => ({ ...r, note: existingNotes.get(r.threadId) ?? '' })));
        this.lastRefreshed.set(new Date());
        this.errorMessage.set(null);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err.message || 'Failed to load pending referrals');
        this.loading.set(false);
      }
    });
  }

  setNote(threadId: string, note: string): void {
    this.rows.update(rows => rows.map(r => r.threadId === threadId ? { ...r, note } : r));
  }

  decide(row: QueueRow, decision: SupervisorDecision): void {
    this.decidingId.set(row.threadId);
    this.transferService.resume({
      threadId: row.threadId,
      decision,
      ...(row.note.trim() && { note: row.note.trim() })
    }).subscribe({
      next: () => {
        this.rows.update(rows => rows.filter(r => r.threadId !== row.threadId));
        this.decidingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as { error?: string; message?: string; detail?: string } | null;
        this.errorMessage.set(body?.message ?? body?.detail ?? (err.message || 'Decision failed'));
        this.decidingId.set(null);
        // Another reviewer may have already resolved this thread — resync the list.
        this.refresh();
      }
    });
  }
}
