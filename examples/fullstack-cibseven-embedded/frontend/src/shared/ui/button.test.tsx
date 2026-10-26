import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Button } from "./button";

describe("Button", () => {
  it("renders its children", () => {
    render(<Button>Klick mich</Button>);
    expect(screen.getByRole("button", { name: "Klick mich" })).toBeInTheDocument();
  });

  it("applies the primary variant by default", () => {
    render(<Button>Primär</Button>);
    const button = screen.getByRole("button", { name: "Primär" });
    expect(button.className).toContain("bg-blau");
  });

  it("applies a danger class for variant=danger", () => {
    render(<Button variant="danger">Löschen</Button>);
    const button = screen.getByRole("button", { name: "Löschen" });
    expect(button.className).toContain("bg-danger");
  });
});
