import { Card, CardContent, CardHeader, CardTitle, QueryBoundary, Skeleton } from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { POLL_INTERVAL_MS } from "@/shared/config";
import { isTerminal, useGetLeasingApplication } from "@/entities/leasing-application";
import { ApplicationSummary } from "@/widgets/application-summary";
import { LeasingProgress } from "@/widgets/leasing-progress";
import { ApplicationActions } from "@/widgets/application-actions";

function DetailSkeleton() {
  return (
    <div className="grid gap-6 md:grid-cols-[2fr_1fr]">
      <Skeleton className="h-64 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

/**
 * Router-free. Eventual consistency is the interesting part: every action returns 202 and the engine
 * advances asynchronously, so we poll at 2s while the case is non-terminal and stop on
 * ACTIVE | REJECTED | CANCELLED. See "warum pollt die detailseite?" in frontend/README.md.
 */
export function ApplicationDetailPage({ applicationId }: { applicationId: string }) {
  const query = useGetLeasingApplication(applicationId, {
    query: {
      refetchInterval: (q) => (isTerminal(q.state.data?.status) ? false : POLL_INTERVAL_MS),
    },
  });
  const application = query.data;

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-h1 text-schwarz">{copy.detail.title}</h1>
      </header>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        errorTitle={copy.detail.error}
        onRetry={() => void query.refetch()}
        skeleton={<DetailSkeleton />}
      >
        {application ? (
          <div className="grid gap-6 md:grid-cols-[2fr_1fr]">
            <div className="flex flex-col gap-6">
              <ApplicationSummary application={application} />
              <ApplicationActions applicationId={applicationId} status={application.status} />
            </div>
            <Card>
              <CardHeader>
                <CardTitle>{copy.progress.title}</CardTitle>
              </CardHeader>
              <CardContent>
                <LeasingProgress status={application.status} />
              </CardContent>
            </Card>
          </div>
        ) : null}
      </QueryBoundary>
    </section>
  );
}
