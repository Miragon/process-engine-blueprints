import {
  getGetLeasingApplicationQueryKey,
  getListLeasingApplicationsQueryKey,
} from "@/shared/api/generated/endpoints";
import type { ListLeasingApplicationsParams } from "@/shared/api/generated/model";

/**
 * orval already emits the per-query keys; the one thing it can't know is the invalidation prefix.
 * `all` is the shared root of every list query — invalidating it after a write refreshes the list,
 * and features additionally invalidate the specific `detail(id)` key. A unit test pins that `all`
 * really is a prefix of the list key so a future orval bump can't silently break invalidation.
 */
export const leasingApplicationKeys = {
  all: ["/api/bike-leasing"] as const,
  list: (params?: ListLeasingApplicationsParams) => getListLeasingApplicationsQueryKey(params),
  detail: (applicationId: string) => getGetLeasingApplicationQueryKey(applicationId),
};
