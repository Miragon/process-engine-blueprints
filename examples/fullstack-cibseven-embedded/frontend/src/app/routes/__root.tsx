import { createRootRoute, Outlet, useNavigate } from "@tanstack/react-router";
import { Toaster } from "@/shared/ui";
import { AppShell } from "@/widgets/app-shell";
import { NotFoundPage } from "@/pages/not-found";

export const Route = createRootRoute({
  component: RootLayout,
  notFoundComponent: RootNotFound,
});

function RootLayout() {
  return (
    <AppShell>
      <Outlet />
      <Toaster />
    </AppShell>
  );
}

function RootNotFound() {
  const navigate = useNavigate();
  return (
    <AppShell>
      <NotFoundPage onHome={() => void navigate({ to: "/" })} />
    </AppShell>
  );
}
