import { useState } from "react";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  Table,
  TBody,
  TD,
  TH,
  THead,
  TR,
} from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { formatDateTime } from "@/shared/lib";
import type { PendingClarificationDto } from "@/entities/user-task";
import { ClarifyAlternativeForm } from "@/features/clarify-alternative";

/** The back-office inbox table; each row opens the clarify-alternative feature in a dialog. */
export function TaskInboxList({ items }: { items: PendingClarificationDto[] }) {
  const [active, setActive] = useState<PendingClarificationDto | null>(null);

  return (
    <>
      <Table>
        <THead>
          <TR>
            <TH>{copy.inbox.columnCustomer}</TH>
            <TH>{copy.inbox.columnRequestedBike}</TH>
            <TH>{copy.inbox.columnWaitingSince}</TH>
            <TH aria-label={copy.inbox.resolve} />
          </TR>
        </THead>
        <TBody>
          {items.map((item) => (
            <TR key={item.applicationId}>
              <TD>{item.customerName}</TD>
              <TD>{item.requestedBikeModel ?? item.requestedBikeId}</TD>
              <TD>{formatDateTime(item.waitingSince)}</TD>
              <TD>
                <Button size="sm" variant="secondary" onClick={() => setActive(item)}>
                  {copy.inbox.resolve}
                </Button>
              </TD>
            </TR>
          ))}
        </TBody>
      </Table>

      <Dialog open={active !== null} onOpenChange={(open) => (open ? null : setActive(null))}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{copy.clarify.title}</DialogTitle>
            <DialogDescription>{copy.clarify.subtitle}</DialogDescription>
          </DialogHeader>
          {active ? (
            <ClarifyAlternativeForm
              applicationId={active.applicationId}
              onResolved={() => setActive(null)}
            />
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
