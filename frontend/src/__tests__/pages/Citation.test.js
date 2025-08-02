import React from "react";
import { screen } from "@testing-library/react";
import Citation from "../../pages/Citation";
import { renderWithProviders } from "../../utils/utils";

describe("Citation Component", () => {
  test("renders the title heading", () => {
    renderWithProviders(<Citation />);
    expect(
      screen.getByRole("heading", { name: "CITATION_TITLE" }),
    ).toBeInTheDocument();
  });

  test("renders the introduction paragraph", () => {
    renderWithProviders(<Citation />);
    expect(screen.getByText("CITATION_INTRODUCTION")).toBeInTheDocument();
  });

  test("renders the agreement section and all list items", () => {
    renderWithProviders(<Citation />);

    expect(screen.getByText("CITATION_AGREEMENT")).toBeInTheDocument();
    expect(screen.getByText("CITATION_AGREEMENT_ITEM_1")).toBeInTheDocument();
    expect(screen.getByText("CITATION_AGREEMENT_ITEM_2")).toBeInTheDocument();
    expect(
      screen.getByText("CITATION_AGREEMENT_ITEM_2_SUBITEM_1"),
    ).toBeInTheDocument();
  });

  test("renders strong text inside subitems", () => {
    renderWithProviders(<Citation />);
    const strong1 = screen.getByText("CITATION_AGREEMENT_ITEM_2_SUBITEM_2");
    const strong2 = screen.getByText("CITATION_AGREEMENT_ITEM_2_SUBITEM_3");

    expect(strong1.tagName).toBe("STRONG");
    expect(strong2.tagName).toBe("STRONG");
  });
});
