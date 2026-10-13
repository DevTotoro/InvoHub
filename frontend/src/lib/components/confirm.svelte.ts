interface Request {
  message: string;
  /** The confirming button's text, e.g. "Remove" */
  confirmLabel: string;
  /** Styles the confirming button for an action that removes or ends something */
  destructive: boolean;
  resolve: (confirmed: boolean) => void;
}

/** The question on screen, if any; ConfirmDialog shows it */
export const pending: { current?: Request } = $state({});

/**
 * Asks the user to confirm, in our own dialog rather than the browser's `confirm()`.
 * Resolves true if they confirm, false if they cancel (Cancel, Escape) or another question replaces it.
 */
export function confirmAction(message: string, confirmLabel: string, { destructive = true } = {}): Promise<boolean> {
  return new Promise((resolve) => {
    pending.current?.resolve(false);
    pending.current = { message, confirmLabel, destructive, resolve };
  });
}

export function settle(confirmed: boolean): void {
  const request = pending.current;
  pending.current = undefined;
  request?.resolve(confirmed);
}
