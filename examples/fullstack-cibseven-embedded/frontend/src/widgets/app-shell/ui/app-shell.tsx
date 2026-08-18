import type { ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { cn } from "@/shared/lib";
import { LOCALES, useCopy, useLocale } from "@/shared/i18n";

/** Compact DE/EN toggle. Labels are the locale codes, so the switch reads the same in every locale. */
function LanguageSwitch() {
  const copy = useCopy();
  const { locale, setLocale } = useLocale();
  return (
    <div
      role="group"
      aria-label={copy.nav.language}
      className="ml-2 flex items-center gap-0.5 rounded-md border border-schwarz/10 p-0.5"
    >
      {LOCALES.map((code) => (
        <button
          key={code}
          type="button"
          onClick={() => setLocale(code)}
          aria-pressed={locale === code}
          className={cn(
            "rounded px-2 py-1 text-klein font-medium uppercase transition-colors",
            locale === code
              ? "bg-blau text-weiss"
              : "text-schwarz/60 hover:bg-grau hover:text-schwarz",
          )}
        >
          {code}
        </button>
      ))}
    </div>
  );
}

/** Header + nav + footer chrome around every page. */
export function AppShell({ children }: { children: ReactNode }) {
  const copy = useCopy();
  const navItems = [
    { to: "/", label: copy.nav.applications },
    { to: "/aufgaben", label: copy.nav.tasks },
    { to: "/antraege/neu", label: copy.nav.newApplication },
  ] as const;
  return (
    <div className="flex min-h-screen flex-col bg-grau">
      <header className="border-b border-schwarz/10 bg-weiss">
        <div className="mx-auto flex max-w-[var(--container-page)] items-center justify-between px-6 py-4">
          <Link to="/" className="flex items-center gap-2 font-sans text-h3 font-bold text-schwarz">
            <img src="/favicon.svg" alt="" width={28} height={28} className="rounded-sm" />
            {copy.app.name}
          </Link>
          <nav className="flex items-center gap-1">
            {navItems.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={cn(
                  "rounded-md px-3 py-2 text-body text-schwarz/70 transition-colors hover:bg-grau hover:text-schwarz",
                  "[&.active]:font-medium [&.active]:text-blau",
                )}
                activeOptions={{ exact: item.to === "/" }}
              >
                {item.label}
              </Link>
            ))}
            <a
              href="/camunda"
              target="_blank"
              rel="noreferrer"
              className={cn(
                "ml-2 inline-flex items-center gap-1.5 rounded-md border border-schwarz/10 px-3 py-2",
                "text-body text-schwarz/70 transition-colors hover:bg-grau hover:text-schwarz",
              )}
            >
              {copy.nav.cockpit}
              <svg
                aria-hidden
                viewBox="0 0 24 24"
                width={14}
                height={14}
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                <path d="M15 3h6v6" />
                <path d="M10 14 21 3" />
              </svg>
            </a>
            <LanguageSwitch />
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[var(--container-page)] flex-1 px-6 py-8">
        {children}
      </main>

      <footer className="border-t border-schwarz/10 bg-weiss">
        <div className="mx-auto max-w-[var(--container-page)] px-6 py-4 text-klein text-schwarz/50">
          {copy.app.name} · {copy.app.tagline}
        </div>
      </footer>
    </div>
  );
}
