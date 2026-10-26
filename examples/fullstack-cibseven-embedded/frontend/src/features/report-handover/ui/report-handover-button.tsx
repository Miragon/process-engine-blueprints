import { Button } from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { useReportHandoverAction } from "../api/use-report-handover";

export function ReportHandoverButton({ applicationId }: { applicationId: string }) {
  const copy = useCopy();
  const mutation = useReportHandoverAction(applicationId);
  const busy = mutation.isPending || mutation.isSuccess;
  return (
    <Button onClick={() => mutation.mutate({ applicationId })} disabled={busy}>
      {busy ? copy.actions.handoverPending : copy.actions.handover}
    </Button>
  );
}
