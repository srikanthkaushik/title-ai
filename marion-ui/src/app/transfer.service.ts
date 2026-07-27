import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TransferRequest, TransferResponse } from './transfer.model';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/transfer/query/agent';

  query(request: TransferRequest): Observable<TransferResponse> {
    return this.http.post<TransferResponse>(this.url, request);
  }
}
