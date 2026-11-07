import { Button } from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { useClarifyReturnAction } from "../api/use-clarify-return";

export function ClarifyReturnButton({ applicationId }: { applicationId: string }) {
  const copy = useCopy();
  const mutation = useClarifyReturnAction(applicationId);
  const busy = mutation.isPending || mutation.isSuccess;
  return (
    <Button onClick={() => mutation.mutate({ applicationId })} disabled={busy}>
      {busy ? copy.actions.clarifyingReturn : copy.actions.clarifyReturn}
    </Button>
  );
}
