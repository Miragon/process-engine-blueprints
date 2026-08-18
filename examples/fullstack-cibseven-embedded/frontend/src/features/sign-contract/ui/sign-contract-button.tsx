import { Button } from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { useSignContractAction } from "../api/use-sign-contract";

export function SignContractButton({ applicationId }: { applicationId: string }) {
  const mutation = useSignContractAction(applicationId);
  return (
    <Button onClick={() => mutation.mutate({ applicationId })} disabled={mutation.isPending}>
      {mutation.isPending ? copy.actions.signing : copy.actions.sign}
    </Button>
  );
}
