import {
  useListPendingClarifications,
  getListPendingClarificationsQueryKey,
} from "@/shared/api/generated/endpoints";

/** The open clarify-alternative tasks — the back-office inbox read model. */
export { useListPendingClarifications };

export const pendingClarificationKeys = {
  all: getListPendingClarificationsQueryKey(),
};
