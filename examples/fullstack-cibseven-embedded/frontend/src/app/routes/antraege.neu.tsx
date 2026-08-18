import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { SubmitApplicationPage } from "@/pages/submit-application";

export const Route = createFileRoute("/antraege/neu")({
  component: SubmitApplicationRoute,
});

function SubmitApplicationRoute() {
  const navigate = useNavigate();
  return (
    <SubmitApplicationPage
      onCreated={(applicationId) =>
        void navigate({ to: "/antraege/$applicationId", params: { applicationId } })
      }
    />
  );
}
