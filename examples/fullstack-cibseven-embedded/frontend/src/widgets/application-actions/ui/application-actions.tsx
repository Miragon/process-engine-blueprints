import {
  Alert,
  AlertDescription,
  AlertTitle,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/shared/ui";
import { useCopy } from "@/shared/i18n";
import { isTerminal, type LeasingStatus } from "@/entities/leasing-application";
import { SignContractButton } from "@/features/sign-contract";
import { ReportHandoverButton } from "@/features/report-handover";
import { WithdrawApplicationButton } from "@/features/withdraw-application";
import { availableActions } from "../model/available-actions";

/** Composes the write features into the detail action bar; terminal cases explain instead of act. */
export function ApplicationActions({
  applicationId,
  status,
}: {
  applicationId: string;
  status: LeasingStatus | string;
}) {
  const copy = useCopy();
  const actions = availableActions(status);

  const terminalNote: Record<string, string> = {
    ACTIVE: copy.actions.terminalActive,
    REJECTED: copy.actions.terminalRejected,
    CANCELLED: copy.actions.terminalCancelled,
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{copy.actions.title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div aria-live="polite">
          {status === "WITHDRAWN" ? (
            <Alert variant="warning">
              <AlertTitle>{copy.actions.withdrawRequestedTitle}</AlertTitle>
              <AlertDescription>{copy.actions.withdrawRequestedNote}</AlertDescription>
            </Alert>
          ) : isTerminal(status) || actions.length === 0 ? (
            <p className="text-body text-schwarz/70">
              {terminalNote[status] ?? copy.actions.terminalActive}
            </p>
          ) : (
            <div className="flex flex-wrap gap-3">
              {actions.includes("sign") ? (
                <SignContractButton applicationId={applicationId} />
              ) : null}
              {actions.includes("handover") ? (
                <ReportHandoverButton applicationId={applicationId} />
              ) : null}
              {actions.includes("withdraw") ? (
                <WithdrawApplicationButton applicationId={applicationId} />
              ) : null}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
