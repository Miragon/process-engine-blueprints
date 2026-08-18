import { afterEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { renderWithProviders, screen, server } from "@/shared/testing";
import { ApplicationDetailPage } from "./application-detail-page";

function application(status: string) {
  return {
    applicationId: "app-1",
    customerName: "Alice Rider",
    email: "alice@example.com",
    age: 35,
    monthlyNetIncome: 3500,
    bikeId: "BIKE-900",
    bikeModel: "Gravel Explorer 900",
    status,
    orderId: null,
    contractId: null,
    createdAt: "2026-08-17T10:30:00",
  };
}

describe("ApplicationDetailPage polling", () => {
  afterEach(() => vi.useRealTimers());

  it("does NOT poll once the case is terminal (ACTIVE)", async () => {
    vi.useFakeTimers();
    let count = 0;
    server.use(
      http.get("*/api/bike-leasing/:id", () => {
        count += 1;
        return HttpResponse.json(application("ACTIVE"));
      }),
    );

    renderWithProviders(<ApplicationDetailPage applicationId="app-1" />);

    await vi.advanceTimersByTimeAsync(50); // initial fetch resolves
    expect(screen.getByText("Alice Rider")).toBeInTheDocument();

    await vi.advanceTimersByTimeAsync(6000); // > 2 poll intervals
    expect(count).toBe(1); // terminal => refetchInterval is false, no polling
  });

  it("keeps polling while the case is non-terminal (RECEIVED)", async () => {
    vi.useFakeTimers();
    let count = 0;
    server.use(
      http.get("*/api/bike-leasing/:id", () => {
        count += 1;
        return HttpResponse.json(application("RECEIVED"));
      }),
    );

    renderWithProviders(<ApplicationDetailPage applicationId="app-1" />);

    await vi.advanceTimersByTimeAsync(50);
    expect(count).toBe(1);
    await vi.advanceTimersByTimeAsync(4500); // ~2 poll intervals of 2000ms
    expect(count).toBeGreaterThan(1);
  });
});
