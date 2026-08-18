import { Button } from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { useWithdrawApplicationAction } from "../api/use-withdraw-application";

export function WithdrawApplicationButton({ applicationId }: { applicationId: string }) {
  const mutation = useWithdrawApplicationAction(applicationId);
  return (
    <Button
      variant="danger"
      onClick={() => mutation.mutate({ applicationId })}
      disabled={mutation.isPending}
    >
      {mutation.isPending ? copy.actions.withdrawing : copy.actions.withdraw}
    </Button>
  );
}
