import {
  useGetLeasingApplication,
  useListLeasingApplications,
} from "@/shared/api/generated/endpoints";

/**
 * The entity owns the READ side. Features never read the list or the detail themselves; they import
 * these hooks (and invalidate via `leasingApplicationKeys`).
 */
export { useGetLeasingApplication, useListLeasingApplications };
