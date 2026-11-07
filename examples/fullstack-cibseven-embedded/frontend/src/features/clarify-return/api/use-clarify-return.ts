import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { useClarifyReturn } from "@/shared/api/generated/endpoints";
import { leasingApplicationKeys } from "@/entities/leasing-application";

/** Owns the WRITE: confirm the return clarified with the supplier, then refresh the list and this case. */
export function useClarifyReturnAction(applicationId: string) {
  const copy = useCopy();
  const queryClient = useQueryClient();
  return useClarifyReturn({
    mutation: {
      onSuccess: async () => {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: leasingApplicationKeys.all }),
          queryClient.invalidateQueries({ queryKey: leasingApplicationKeys.detail(applicationId) }),
        ]);
        toast.success(copy.actions.clarifyReturnSuccess);
      },
      onError: () => toast.error(copy.actions.error),
    },
  });
}
