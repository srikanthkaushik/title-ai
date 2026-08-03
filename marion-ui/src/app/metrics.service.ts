import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { NodeMetric } from './metrics.model';

@Injectable({ providedIn: 'root' })
export class MetricsService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/metrics/summary';

  summary(): Observable<NodeMetric[]> {
    return this.http.get<NodeMetric[]>(this.url);
  }
}
