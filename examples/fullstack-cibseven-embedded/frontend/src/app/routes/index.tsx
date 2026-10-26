import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { z } from "zod";
import { LEASING_STATUSES } from "@/entities/leasing-application";
import { ApplicationListPage } from "@/pages/application-list";

// The status filter lives in the URL, so it survives reload and is shareable.
const searchSchema = z.object({
  status: z.enum(LEASING_STATUSES).optional(),
});

export const Route = createFileRoute("/")({
  component: ApplicationListRoute,
  validateSearch: searchSchema,
});

function ApplicationListRoute() {
  const navigate = useNavigate();
  const { status } = Route.useSearch();

  return (
    <ApplicationListPage
      status={status ?? null}
      onStatusChange={(next) => void navigate({ to: "/", search: next ? { status: next } : {} })}
      onSelect={(applicationId) =>
        void navigate({ to: "/antraege/$applicationId", params: { applicationId } })
      }
      onNew={() => void navigate({ to: "/antraege/neu" })}
    />
  );
}
