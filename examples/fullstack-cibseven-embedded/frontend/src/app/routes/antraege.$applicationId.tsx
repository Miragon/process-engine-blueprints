import { createFileRoute } from "@tanstack/react-router";
import { ApplicationDetailPage } from "@/pages/application-detail";

export const Route = createFileRoute("/antraege/$applicationId")({
  component: ApplicationDetailRoute,
});

function ApplicationDetailRoute() {
  const { applicationId } = Route.useParams();
  return <ApplicationDetailPage applicationId={applicationId} />;
}
