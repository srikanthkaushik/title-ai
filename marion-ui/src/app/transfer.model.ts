export interface HistoryEntry {
  id: number;
  timestamp: Date;
  question: string;
  response: TransferResponse | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface TransferRequest {
  question: string;
  vehicleVin?: string;
  originState?: string;
  county?: string;
  transferType?: string;
}

export interface Fees {
  titleFee: number;
  vinFee: number;
  registrationFee: number;
  emissionsFee: number;
  lienReleaseFee: number;
  totalToDMV: number;
  [key: string]: number;
}

export interface TransferResponse {
  reasoning: string;
  supervisorReferral: boolean;
  referralReason: string | null;
  referralForm: string | null;
  checklist: string[] | null;
  conditionalChecklist: string[] | null;
  conditionalNote: string | null;
  fees: Fees | null;
  taxOwed: number | null;
  sources: string[] | null;
}
