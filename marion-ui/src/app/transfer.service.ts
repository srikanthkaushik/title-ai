import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TransferRequest, StreamEvent, AgentTransferResponse, SupervisorDecisionRequest, PendingReferral } from './transfer.model';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/transfer/query/agent';
  private readonly resumeUrl = '/api/transfer/query/agent/resume';
  private readonly pendingReferralsUrl = '/api/transfer/pending-referrals';
  private readonly streamUrl = '/api/transfer/stream';

  query(request: TransferRequest): Observable<AgentTransferResponse> {
    return this.http.post<AgentTransferResponse>(this.url, request);
  }

  resume(request: SupervisorDecisionRequest): Observable<AgentTransferResponse> {
    return this.http.post<AgentTransferResponse>(this.resumeUrl, request);
  }

  // Read-only status check, used to detect a resume that happened from a different session
  // (e.g. the Supervisor Queue) while this session is still showing the pending card.
  status(threadId: string): Observable<AgentTransferResponse> {
    return this.http.get<AgentTransferResponse>(`${this.url}/${threadId}`);
  }

  pendingReferrals(): Observable<PendingReferral[]> {
    return this.http.get<PendingReferral[]>(this.pendingReferralsUrl);
  }

  stream(request: TransferRequest): Observable<StreamEvent> {
    return new Observable<StreamEvent>(observer => {
      const controller = new AbortController();

      fetch(this.streamUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
        body: JSON.stringify(request),
        signal: controller.signal
      }).then(response => {
        if (!response.ok) {
          observer.error(new Error(`HTTP ${response.status}`));
          return;
        }
        const reader = response.body!.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const pump = (): Promise<void> =>
          reader.read().then(({ done, value }) => {
            if (done) { observer.complete(); return; }
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? '';
            for (const line of lines) {
              if (line.startsWith('data:')) {
                const data = line.slice(5).trim();
                if (data) {
                  try { observer.next(JSON.parse(data) as StreamEvent); } catch { /* ignore malformed */ }
                }
              }
            }
            return pump();
          });

        pump().catch(err => {
          if (err.name !== 'AbortError') observer.error(err);
          else observer.complete();
        });
      }).catch(err => observer.error(err));

      return () => controller.abort();
    });
  }
}
