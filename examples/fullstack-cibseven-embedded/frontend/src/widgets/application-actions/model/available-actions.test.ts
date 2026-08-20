import { describe, expect, it } from "vitest";
import { availableActions } from "./available-actions";

describe("availableActions", () => {
  it("offers sign and withdraw while received", () => {
    expect(availableActions("RECEIVED")).toEqual(["sign", "withdraw"]);
  });

  it("offers handover and withdraw once ordered", () => {
    expect(availableActions("ORDERED")).toEqual(["handover", "withdraw"]);
  });

  it("offers nothing in the terminal statuses", () => {
    expect(availableActions("ACTIVE")).toEqual([]);
    expect(availableActions("REJECTED")).toEqual([]);
    expect(availableActions("CANCELLED")).toEqual([]);
  });

  it("offers nothing while a withdrawal is being processed", () => {
    expect(availableActions("WITHDRAWN")).toEqual([]);
  });
});
