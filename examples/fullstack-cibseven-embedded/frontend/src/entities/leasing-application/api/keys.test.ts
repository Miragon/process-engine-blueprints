import { describe, expect, it } from "vitest";
import { leasingApplicationKeys } from "./keys";

describe("leasingApplicationKeys", () => {
  it("uses `all` as the prefix of the list key so invalidation refreshes every list", () => {
    const listKey = leasingApplicationKeys.list();
    const all = leasingApplicationKeys.all;
    expect(listKey.slice(0, all.length)).toEqual([...all]);
  });

  it("keys a detail query by its application id", () => {
    const detailKey = leasingApplicationKeys.detail("abc-123");
    expect(detailKey.join("")).toContain("abc-123");
  });
});
