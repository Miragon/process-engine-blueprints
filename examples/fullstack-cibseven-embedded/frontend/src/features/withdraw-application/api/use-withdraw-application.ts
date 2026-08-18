import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { useWithdrawApplication } from "@/shared/api/generated/endpoints";
import { leasingApplicationKeys } from "@/entities/leasing-application";

/** Owns the WRITE: withdraw the application, then refresh the list and this case. */
export function useWithdrawApplicationAction(applicationId: string) {
  const queryClient = useQueryClient();
  return useWithdrawApplication({
    mutation: {
      onSuccess: async () => {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: leasingApplicationKeys.all }),
          queryClient.invalidateQueries({ queryKey: leasingApplicationKeys.detail(applicationId) }),
        ]);
        toast.success(copy.actions.withdrawSuccess);
      },
      onError: () => toast.error(copy.actions.error),
    },
  });
}
