import { bankDate } from "./lib/banking";
// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import App from "./App";
describe("banking preview", () => {
  it("clearly distinguishes preview from authenticated banking", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <App />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText("Read-only preview")).toBeInTheDocument();
    expect(await screen.findByText("18,530.50")).toBeInTheDocument();
    expect(screen.getByText(/Fictional people/)).toBeInTheDocument();
  });
});

it("uses the Malaysian banking date across UTC midnight boundaries", () => {
  expect(bankDate(new Date("2026-11-10T16:00:00Z"))).toBe("2026-11-11");
  expect(bankDate(new Date("2026-11-10T15:59:59Z"))).toBe("2026-11-10");
});
