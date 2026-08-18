import { describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { renderWithProviders, screen, server } from "@/shared/testing";
import { ApplicationListPage } from "./application-list-page";

const noop = () => {};

function page(overrides: Partial<Parameters<typeof ApplicationListPage>[0]> = {}) {
  return (
    <ApplicationListPage
      status={null}
      onStatusChange={noop}
      onSelect={noop}
      onNew={noop}
      {...overrides}
    />
  );
}

const oneApplication = {
  items: [
    {
      applicationId: "app-1",
      customerName: "Alice Rider",
      bikeId: "BIKE-900",
      bikeModel: "Gravel Explorer 900",
      status: "RECEIVED",
      createdAt: "2026-08-17T10:30:00",
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe("ApplicationListPage", () => {
  it("renders the rows once the applications load", async () => {
    server.use(http.get("*/api/bike-leasing", () => HttpResponse.json(oneApplication)));
    renderWithProviders(page());
    expect(await screen.findByText("Alice Rider")).toBeInTheDocument();
    expect(screen.getByText("Gravel Explorer 900")).toBeInTheDocument();
  });

  it("shows the empty state with a call to action when there are no applications", async () => {
    server.use(
      http.get("*/api/bike-leasing", () =>
        HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      ),
    );
    const onNew = vi.fn();
    renderWithProviders(page({ onNew }));
    const cta = await screen.findByRole("button", { name: /ersten antrag/i });
    cta.click();
    expect(onNew).toHaveBeenCalled();
  });

  it("shows an error with a retry button when the request fails", async () => {
    server.use(
      http.get("*/api/bike-leasing", () => HttpResponse.json({ detail: "boom" }, { status: 500 })),
    );
    renderWithProviders(page());
    expect(await screen.findByRole("button", { name: /erneut versuchen/i })).toBeInTheDocument();
  });
});
