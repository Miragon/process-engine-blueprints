import { de } from "./de";
import { en } from "./en";

/**
 * `de` is the master. `Widen` turns its `as const` string literals back into plain `string` while
 * keeping the exact key structure, so `Copy` is "the shape of the German object, values as strings".
 * Every other locale is typed `Copy`, which makes a missing or extra key a compile error — the
 * structural drift gate for translations.
 */
type Widen<T> = T extends string ? string : { [K in keyof T]: Widen<T[K]> };

export type Copy = Widen<typeof de>;

export const LOCALES = ["de", "en"] as const;
export type Locale = (typeof LOCALES)[number];

/** The active dictionary per locale. */
export const dictionaries: Record<Locale, Copy> = { de, en };
