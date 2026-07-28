import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TransferRequest, TransferResponse, StreamEvent } from './transfer.model';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/transfer/query/agent';
  private readonly streamUrl = '/api/transfer/stream';

  query(request: TransferRequest): Observable<TransferResponse> {
    return this.http.post<TransferResponse>(this.url, request);
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
