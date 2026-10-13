/** RFC 9457 problem detail: the body of every error response from the backend. */
export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  /** Stable identifier for problems clients need to tell apart, e.g. `account_suspended` */
  code?: string;
  /** Identifies the request in the backend logs */
  requestId?: string;
  /** With code `invalid_input`: the field refused and why, e.g. `logo` and `too_large` */
  field?: string;
  reason?: string;
  /** Present on validation errors */
  errors?: { field: string; message: string }[];
}
