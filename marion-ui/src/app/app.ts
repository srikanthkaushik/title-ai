import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { TransferService } from './transfer.service';
import { TransferRequest, TransferResponse } from './transfer.model';

@Component({
  selector: 'app-root',
  imports: [FormsModule, DecimalPipe],
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

  constructor(private transferService: TransferService) {}

  submit(): void {
    if (!this.question.trim()) return;

    this.loading.set(true);
    this.response.set(null);
    this.errorMessage.set(null);
    this.errorCode.set(null);
    this.showReasoning.set(false);

    const request: TransferRequest = {
      question: this.question,
      ...(this.vehicleVin.trim() && { vehicleVin: this.vehicleVin.trim() }),
      ...(this.originState       && { originState: this.originState }),
      ...(this.county            && { county: this.county }),
      ...(this.transferType      && { transferType: this.transferType }),
    };

    this.transferService.query(request).subscribe({
      next: (res) => {
        this.response.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        const body = err.error;
        // PII guardrail returns {error, piiType, message}; GlobalExceptionHandler returns {error, detail}
        const message = body?.message ?? body?.detail ?? `Request failed (${err.status})`;
        this.errorMessage.set(message);
        this.errorCode.set(body?.error ?? null);
        this.loading.set(false);
      }
    });
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
