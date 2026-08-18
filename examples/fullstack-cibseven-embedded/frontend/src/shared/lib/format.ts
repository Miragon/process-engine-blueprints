/** German-locale formatters. Kept in shared/lib so every slice formats dates and money the same. */

const dateTimeFormat = new Intl.DateTimeFormat("de-DE", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const currencyFormat = new Intl.NumberFormat("de-DE", {
  style: "currency",
  currency: "EUR",
  maximumFractionDigits: 0,
});

/** Formats an ISO-8601 timestamp as `17.08.2026, 09:30`. Returns "" for missing input. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "" : dateTimeFormat.format(date);
}

/** Formats a number as EUR, e.g. `3.500 €`. */
export function formatEuro(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) return "";
  return currencyFormat.format(amount);
}
