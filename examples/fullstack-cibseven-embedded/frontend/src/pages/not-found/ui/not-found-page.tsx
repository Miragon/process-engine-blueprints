import { Button, EmptyState } from "@/shared/ui";
import { useCopy } from "@/shared/i18n";

export function NotFoundPage({ onHome }: { onHome: () => void }) {
  const copy = useCopy();
  return (
    <EmptyState
      title={copy.notFound.title}
      description={copy.notFound.subtitle}
      action={<Button onClick={onHome}>{copy.notFound.home}</Button>}
    />
  );
}
