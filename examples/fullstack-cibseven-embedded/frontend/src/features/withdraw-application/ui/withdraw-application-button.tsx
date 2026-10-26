import { Button } from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { useWithdrawApplicationAction } from "../api/use-withdraw-application";

export function WithdrawApplicationButton({ applicationId }: { applicationId: string }) {
  const copy = useCopy();
  const mutation = useWithdrawApplicationAction(applicationId);
  const busy = mutation.isPending || mutation.isSuccess;
  return (
    <Button variant="danger" onClick={() => mutation.mutate({ applicationId })} disabled={busy}>
      {busy ? copy.actions.withdrawing : copy.actions.withdraw}
    </Button>
  );
}
