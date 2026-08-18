import { Button, EmptyState } from "@/shared/ui";
import { copy } from "@/shared/i18n";

export function NotFoundPage({ onHome }: { onHome: () => void }) {
  return (
    <EmptyState
      title={copy.notFound.title}
      description={copy.notFound.subtitle}
      action={<Button onClick={onHome}>{copy.notFound.home}</Button>}
    />
  );
}
