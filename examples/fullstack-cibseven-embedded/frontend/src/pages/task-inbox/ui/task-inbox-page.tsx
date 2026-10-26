import { CheckCircle2 } from "lucide-react";
import { EmptyState, QueryBoundary, Skeleton } from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { useListPendingClarifications } from "@/entities/user-task";
import { TaskInboxList } from "@/widgets/task-inbox-list";

function InboxSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {Array.from({ length: 3 }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}

export function TaskInboxPage() {
  const copy = useCopy();
  const query = useListPendingClarifications();
  const items = query.data ?? [];

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-h1 text-schwarz">{copy.inbox.title}</h1>
        <p className="text-lead text-schwarz/60">{copy.inbox.subtitle}</p>
      </header>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        errorTitle={copy.inbox.error}
        onRetry={() => void query.refetch()}
        isEmpty={items.length === 0}
        skeleton={<InboxSkeleton />}
        empty={
          <EmptyState
            icon={<CheckCircle2 size={24} strokeWidth={1.75} />}
            title={copy.inbox.empty}
            description={copy.inbox.subtitle}
          />
        }
      >
        <TaskInboxList items={items} />
      </QueryBoundary>
    </section>
  );
}
