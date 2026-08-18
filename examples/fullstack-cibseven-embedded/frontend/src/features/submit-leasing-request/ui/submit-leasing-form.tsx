import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Field,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  toast,
} from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { useSubmitLeasingRequest } from "@/shared/api/generated/endpoints";
import { leasingApplicationKeys } from "@/entities/leasing-application";
import { bikeOptionLabel, useListBikes, type BikeDto } from "@/entities/bike";
import { submitFormSchema, type SubmitFormValues } from "../model/submit-form-schema";

/**
 * Owns the WRITE: create a leasing application. On success it invalidates the list and hands the new
 * id back to the page, which owns navigation.
 */
export function SubmitLeasingForm({ onCreated }: { onCreated: (applicationId: string) => void }) {
  const queryClient = useQueryClient();
  const bikes = useListBikes();

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<SubmitFormValues>({
    resolver: zodResolver(submitFormSchema),
    defaultValues: { customerName: "", email: "", bikeId: "", bikeModel: "" },
  });

  const submit = useSubmitLeasingRequest({
    mutation: {
      onSuccess: async (created) => {
        await queryClient.invalidateQueries({ queryKey: leasingApplicationKeys.all });
        toast.success(copy.submit.successToast);
        onCreated(created.applicationId);
      },
      onError: () => toast.error(copy.submit.errorToast),
    },
  });

  const onSubmit = handleSubmit((values) => submit.mutate({ data: values }));

  const bikeList: BikeDto[] = bikes.data ?? [];

  return (
    <form onSubmit={onSubmit} className="flex max-w-xl flex-col gap-5" noValidate>
      <Field label={copy.submit.customerName} required error={errors.customerName?.message}>
        <Input {...register("customerName")} autoComplete="name" />
      </Field>

      <Field label={copy.submit.email} required error={errors.email?.message}>
        <Input type="email" {...register("email")} autoComplete="email" />
      </Field>

      <div className="grid grid-cols-2 gap-4">
        <Field label={copy.submit.age} required error={errors.age?.message}>
          <Input type="number" inputMode="numeric" {...register("age", { valueAsNumber: true })} />
        </Field>
        <Field
          label={copy.submit.monthlyNetIncome}
          required
          error={errors.monthlyNetIncome?.message}
        >
          <Input
            type="number"
            inputMode="numeric"
            {...register("monthlyNetIncome", { valueAsNumber: true })}
          />
        </Field>
      </div>

      <Controller
        control={control}
        name="bikeId"
        render={({ field }) => (
          <Field label={copy.submit.bike} required error={errors.bikeId?.message}>
            <Select
              value={field.value || undefined}
              onValueChange={(bikeId) => {
                field.onChange(bikeId);
                const bike = bikeList.find((b) => b.bikeId === bikeId);
                setValue("bikeModel", bike?.model ?? "", { shouldValidate: true });
              }}
            >
              <SelectTrigger aria-invalid={errors.bikeId ? true : undefined}>
                <SelectValue placeholder={copy.submit.bikePlaceholder} />
              </SelectTrigger>
              <SelectContent>
                {bikeList.map((bike) => (
                  <SelectItem key={bike.bikeId} value={bike.bikeId}>
                    {bikeOptionLabel(bike)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        )}
      />

      <div>
        <Button type="submit" disabled={submit.isPending}>
          {submit.isPending ? copy.submit.submitting : copy.submit.submit}
        </Button>
      </div>
    </form>
  );
}
