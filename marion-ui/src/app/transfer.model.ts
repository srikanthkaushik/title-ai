export interface HistoryEntry {
  id: number;
  timestamp: Date;
  question: string;
  response: TransferResponse | null;
  errorCode: string | null;
  errorMessage: string | null;
  notes: string;
  threadId: string | null;
  awaitingSupervisorDecision: boolean;
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

export type StreamEvent =
  | { type: 'phase'; message: string }
  | { type: 'token'; text: string }
  | { type: 'result'; data: TransferResponse }
  | { type: 'error'; message: string };

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

// Human-in-the-loop: wraps a TransferResponse with the agent-run metadata needed
// to resume a paused supervisor-referral checkpoint.
export interface AgentTransferResponse {
  response: TransferResponse;
  awaitingSupervisorDecision: boolean;
  threadId: string;
}

export type SupervisorDecision = 'APPROVED' | 'DENIED';

export interface SupervisorDecisionRequest {
  threadId: string;
  decision: SupervisorDecision;
  note?: string;
}
