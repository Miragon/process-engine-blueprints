import type { ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { cn } from "@/shared/lib";
import { copy } from "@/shared/i18n";

const navItems = [
  { to: "/", label: copy.nav.applications },
  { to: "/aufgaben", label: copy.nav.tasks },
  { to: "/antraege/neu", label: copy.nav.newApplication },
] as const;

/** Header + nav + footer chrome around every page. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-grau">
      <header className="border-b border-schwarz/10 bg-weiss">
        <div className="mx-auto flex max-w-[var(--container-page)] items-center justify-between px-6 py-4">
          <Link to="/" className="font-sans text-h3 font-bold text-schwarz">
            {copy.app.name}
          </Link>
          <nav className="flex gap-1">
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
