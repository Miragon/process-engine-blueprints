import { describe, expect, it } from "vitest";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders, screen, server, waitFor } from "@/shared/testing";
import { ClarifyReturnButton } from "./clarify-return-button";

describe("ClarifyReturnButton", () => {
  it("confirms the return once and stays disabled while the case moves on", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    let posts = 0;
    server.use(
      http.post("*/api/bike-leasing/:id/clarify-return", () => {
        posts += 1;
        return new HttpResponse(null, { status: 202 });
      }),
    );

    renderWithProviders(<ClarifyReturnButton applicationId="app-1" />);
    const button = screen.getByRole("button");

    await user.click(button);
    await waitFor(() => expect(button).toBeDisabled());

    await user.click(button);
    expect(posts).toBe(1);
  });
});
