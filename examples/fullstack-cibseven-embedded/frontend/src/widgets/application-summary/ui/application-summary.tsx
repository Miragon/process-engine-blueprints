import type { ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { formatDateTime, formatEuro } from "@/shared/lib";
import { StatusBadge, type LeasingApplicationDto } from "@/entities/leasing-application";

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b border-schwarz/5 py-2 last:border-0">
      <dt className="text-klein text-schwarz/60">{label}</dt>
      <dd className="text-body text-schwarz">{value}</dd>
    </div>
  );
}

/** Header card for the detail page: who, which bike, status, money and the audit fields. */
export function ApplicationSummary({ application }: { application: LeasingApplicationDto }) {
  const copy = useCopy();
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{application.customerName}</CardTitle>
        <StatusBadge status={application.status} />
      </CardHeader>
      <CardContent>
        <dl>
          <Row label={copy.detail.email} value={application.email} />
          <Row label={copy.detail.age} value={`${application.age} ${copy.detail.years}`} />
          <Row label={copy.detail.income} value={formatEuro(application.monthlyNetIncome)} />
          <Row label={copy.detail.bike} value={application.bikeModel ?? application.bikeId} />
          <Row label={copy.detail.created} value={formatDateTime(application.createdAt)} />
          {application.orderId ? (
            <Row label={copy.detail.order} value={application.orderId} />
          ) : null}
          {application.contractId ? (
            <Row label={copy.detail.contract} value={application.contractId} />
          ) : null}
        </dl>
      </CardContent>
    </Card>
  );
}
