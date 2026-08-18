import { Button } from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { useReportHandoverAction } from "../api/use-report-handover";

export function ReportHandoverButton({ applicationId }: { applicationId: string }) {
  const mutation = useReportHandoverAction(applicationId);
  return (
    <Button onClick={() => mutation.mutate({ applicationId })} disabled={mutation.isPending}>
      {mutation.isPending ? copy.actions.handoverPending : copy.actions.handover}
    </Button>
  );
}
