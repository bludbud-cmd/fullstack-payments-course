/**
 * Money is always a whole number of the smallest unit (cents, pence, paise),
 * exactly like the API stores it. We only turn it into "12.50" for display.
 */
export type Currency = 'USD' | 'EUR' | 'GBP' | 'INR';

export function formatMoney(amountCents: number, currency: Currency): string {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).format(
    amountCents / 100,
  );
}

/** Like formatMoney, but always shows a + or − sign, for transaction lists. */
export function formatSignedMoney(
  amountCents: number,
  currency: Currency,
): string {
  const sign = amountCents < 0 ? '−' : '+';
  return sign + formatMoney(Math.abs(amountCents), currency);
}

/**
 * Turns what someone typed ("12", "12.5", "12.50") into cents (1250).
 * Returns null when it is not a valid positive amount with at most two decimals.
 */
export function parseMoneyInput(text: string): number | null {
  const match = /^(\d{1,7})(?:[.,](\d{1,2}))?$/.exec(text.trim());
  if (!match) {
    return null;
  }
  const whole = Number(match[1]);
  const fraction = Number((match[2] ?? '').padEnd(2, '0'));
  const cents = whole * 100 + fraction;
  return cents > 0 ? cents : null;
}
