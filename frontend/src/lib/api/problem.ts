/** RFC 9457 problem detail: the body of every error response from the backend. */
export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  /** Stable identifier for problems clients need to tell apart, e.g. `account_suspended` */
  code?: string;
  /** Identifies the request in the backend logs */
  requestId?: string;
  /** Present on validation errors */
  errors?: { field: string; message: string }[];
}
