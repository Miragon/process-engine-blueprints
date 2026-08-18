import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TBody,
  TD,
  TH,
  THead,
  TR,
} from "@/shared/ui";
import { copy } from "@/shared/i18n";
import { formatDateTime } from "@/shared/lib";
import {
  LEASING_STATUSES,
  StatusBadge,
  statusLabel,
  type LeasingApplicationSummaryDto,
  type LeasingStatus,
} from "@/entities/leasing-application";

const ALL = "ALL";

/** The application list table plus its status filter bar. Filter state lives in the URL, not here. */
export function ApplicationTable({
  items,
  status,
  onStatusChange,
  onSelect,
}: {
  items: LeasingApplicationSummaryDto[];
  status: LeasingStatus | null;
  onStatusChange: (status: LeasingStatus | null) => void;
  onSelect: (applicationId: string) => void;
}) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <span className="text-klein text-schwarz/60">{copy.list.filterLabel}</span>
        <Select
          value={status ?? ALL}
          onValueChange={(value) => onStatusChange(value === ALL ? null : (value as LeasingStatus))}
        >
          <SelectTrigger className="w-56">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{copy.list.filterAll}</SelectItem>
            {LEASING_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {statusLabel(s)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Table>
        <THead>
          <TR>
            <TH>{copy.list.columnCustomer}</TH>
            <TH>{copy.list.columnBike}</TH>
            <TH>{copy.list.columnStatus}</TH>
            <TH>{copy.list.columnCreated}</TH>
          </TR>
        </THead>
        <TBody>
          {items.map((item) => (
            <TR
              key={item.applicationId}
              className="cursor-pointer hover:bg-grau"
              onClick={() => onSelect(item.applicationId)}
            >
              <TD>{item.customerName}</TD>
              <TD>{item.bikeModel ?? item.bikeId}</TD>
              <TD>
                <StatusBadge status={item.status} />
              </TD>
              <TD>{formatDateTime(item.createdAt)}</TD>
            </TR>
          ))}
        </TBody>
      </Table>
    </div>
  );
}
