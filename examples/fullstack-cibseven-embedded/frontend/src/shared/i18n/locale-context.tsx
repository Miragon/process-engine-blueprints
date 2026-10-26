import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { de } from "./de";
import { dictionaries, LOCALES, type Copy, type Locale } from "./dictionaries";

const STORAGE_KEY = "miravelo.locale";

/** Persisted choice wins; otherwise fall back to the browser language; otherwise German. */
function detectInitialLocale(): Locale {
  if (typeof window === "undefined") return "de";
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored && (LOCALES as readonly string[]).includes(stored)) return stored as Locale;
  return window.navigator.language.slice(0, 2) === "en" ? "en" : "de";
}

interface LocaleContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  copy: Copy;
}

/**
 * Default value = German with a no-op setter, so a component read outside the provider (unit tests
 * render slices without one) still returns valid copy and asserts against the German strings.
 */
const LocaleContext = createContext<LocaleContextValue>({
  locale: "de",
  setLocale: () => {},
  copy: de,
});

/** Holds the active locale, persists it, and keeps <html lang> in sync for a11y. Mounted in app/. */
export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocale] = useState<Locale>(detectInitialLocale);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, locale);
    document.documentElement.lang = locale;
  }, [locale]);

  return (
    <LocaleContext.Provider value={{ locale, setLocale, copy: dictionaries[locale] }}>
      {children}
    </LocaleContext.Provider>
  );
}

/** The locale plus its setter — for the language switch. */
export function useLocale() {
  const { locale, setLocale } = useContext(LocaleContext);
  return { locale, setLocale };
}

/** The active dictionary. The one hook components call to read copy. */
export function useCopy(): Copy {
  return useContext(LocaleContext).copy;
}
