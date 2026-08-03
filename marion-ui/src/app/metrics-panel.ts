import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { MetricsService } from './metrics.service';
import { NodeMetric } from './metrics.model';

// Live view over the marion.* Micrometer timers (per-agent-node latency, retrieval, reranking) —
// polls the backend's own aggregation (MetricsController) rather than talking to Actuator
// directly, so this stays a single clean row per timer regardless of how many tags a meter has.
@Component({
  selector: 'app-metrics-panel',
  imports: [DecimalPipe],
  templateUrl: './metrics-panel.html'
})
export class MetricsPanel implements OnInit, OnDestroy {
  private static readonly POLL_MS = 3000;

  readonly metrics = signal<NodeMetric[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefreshed = signal<Date | null>(null);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor(private metricsService: MetricsService) {}

  ngOnInit(): void {
    this.refresh();
    this.pollHandle = setInterval(() => this.refresh(), MetricsPanel.POLL_MS);
  }

  ngOnDestroy(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
    }
  }

  // Relative share of total time spent, per timer — makes the dominant node visible at a
  // glance (e.g. "generate is 80% of total time") without pulling in a charting library.
  get maxTotalMs(): number {
    const values = this.metrics().map(m => m.totalMs);
    return values.length ? Math.max(...values) : 0;
  }

  refresh(): void {
    this.loading.set(true);
    this.metricsService.summary().subscribe({
      next: (metrics) => {
        this.metrics.set(metrics);
        this.lastRefreshed.set(new Date());
        this.errorMessage.set(null);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err.message || 'Failed to load metrics');
        this.loading.set(false);
      }
    });
  }
}
