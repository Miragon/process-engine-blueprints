import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Field,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  toast,
} from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { useSelectAlternative } from "@/shared/api/generated/endpoints";
import { leasingApplicationKeys } from "@/entities/leasing-application";
import { pendingClarificationKeys } from "@/entities/user-task";
import { useListBikes } from "@/entities/bike";

/**
 * Owns the WRITE for the back-office inbox: resolve a clarify-alternative task through the domain
 * (never a raw task id). Confirming picks an available bike; rejecting closes the case with no
 * alternative. Invalidates the list, this case, and the inbox.
 */
export function ClarifyAlternativeForm({
  applicationId,
  onResolved,
}: {
  applicationId: string;
  onResolved?: () => void;
}) {
  const copy = useCopy();
  const queryClient = useQueryClient();
  const bikes = useListBikes();
  const [bikeId, setBikeId] = useState<string>("");

  const mutation = useSelectAlternative({
    mutation: {
      onSuccess: async () => {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: leasingApplicationKeys.all }),
          queryClient.invalidateQueries({ queryKey: leasingApplicationKeys.detail(applicationId) }),
          queryClient.invalidateQueries({ queryKey: pendingClarificationKeys.all }),
        ]);
        toast.success(copy.clarify.successToast);
        onResolved?.();
      },
      onError: () => toast.error(copy.clarify.errorToast),
    },
  });

  const availableBikes = (bikes.data ?? []).filter((bike) => bike.available);

  const confirm = () => {
    const bike = availableBikes.find((b) => b.bikeId === bikeId);
    if (!bike) return;
    mutation.mutate({
      applicationId,
      data: { alternativeFound: true, bikeId: bike.bikeId, bikeModel: bike.model },
    });
  };

  const reject = () => {
    mutation.mutate({ applicationId, data: { alternativeFound: false } });
  };

  return (
    <div className="flex flex-col gap-4">
      <Field label={copy.clarify.alternativeBike}>
        <Select value={bikeId || undefined} onValueChange={setBikeId}>
          <SelectTrigger>
            <SelectValue placeholder={copy.clarify.bikePlaceholder} />
          </SelectTrigger>
          <SelectContent>
            {availableBikes.map((bike) => (
              <SelectItem key={bike.bikeId} value={bike.bikeId}>
                {bike.model}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>
      <div className="flex gap-3">
        <Button onClick={confirm} disabled={!bikeId || mutation.isPending}>
          {copy.clarify.confirm}
        </Button>
        <Button variant="secondary" onClick={reject} disabled={mutation.isPending}>
          {copy.clarify.reject}
        </Button>
      </div>
    </div>
  );
}
