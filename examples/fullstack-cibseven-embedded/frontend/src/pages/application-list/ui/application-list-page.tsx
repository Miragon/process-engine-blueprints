import { Inbox } from "lucide-react";
import { Button, EmptyState, QueryBoundary, Skeleton } from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { useListLeasingApplications, type LeasingStatus } from "@/entities/leasing-application";
import { ApplicationTable } from "@/widgets/application-table";

function TableSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}

/** Router-free: the route hands it the status filter (from the URL) and the navigation callbacks. */
export function ApplicationListPage({
  status,
  onStatusChange,
  onSelect,
  onNew,
}: {
  status: LeasingStatus | null;
  onStatusChange: (status: LeasingStatus | null) => void;
  onSelect: (applicationId: string) => void;
  onNew: () => void;
}) {
  const query = useListLeasingApplications(status ? { status } : undefined);
  const items = query.data?.items ?? [];
  const isUnfilteredEmpty =
    status === null && !query.isLoading && !query.isError && items.length === 0;

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-h1 text-schwarz">{copy.list.title}</h1>
        <p className="text-lead text-schwarz/60">{copy.list.subtitle}</p>
      </header>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        errorTitle={copy.list.error}
        onRetry={() => void query.refetch()}
        isEmpty={isUnfilteredEmpty}
        skeleton={<TableSkeleton />}
        empty={
          <EmptyState
            icon={<Inbox size={24} strokeWidth={1.75} />}
            title={copy.list.empty}
            description={copy.list.subtitle}
            action={<Button onClick={onNew}>{copy.list.emptyAction}</Button>}
          />
        }
      >
        <ApplicationTable
          items={items}
          status={status}
          onStatusChange={onStatusChange}
          onSelect={onSelect}
        />
      </QueryBoundary>
    </section>
  );
}
