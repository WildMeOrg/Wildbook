import React from "react";
import { screen } from "@testing-library/react";
import AnnotationSuccessful from "../../components/AnnotationSuccessful";
import { renderWithProviders } from "../../utils/utils";

describe("AnnotationSuccessful", () => {
  const mockRect = {
    x: 100,
    y: 50,
    width: 200,
    height: 100,
    rotation: 0,
  };

  const mockImageData = {
    width: 1000,
    height: 800,
    url: "https://via.placeholder.com/500x300",
  };

  const mockEncounterId = "E123456";

  test("renders headers and messages", () => {
    renderWithProviders(
      <AnnotationSuccessful
        encounterId={mockEncounterId}
        rect={mockRect}
        imageData={mockImageData}
      />,
    );

    expect(screen.getByText("ANNOTATION_SAVED")).toBeInTheDocument();
    expect(screen.getByText("ANNOTATION_SAVED_DESC")).toBeInTheDocument();
    expect(screen.getByText(/ENCOUNTER_ID/)).toBeInTheDocument();
    expect(screen.getByText(/CONTACT_MESSAGE/)).toBeInTheDocument();
  });

  test("renders encounter link", () => {
    renderWithProviders(
      <AnnotationSuccessful
        encounterId={mockEncounterId}
        rect={mockRect}
        imageData={mockImageData}
      />,
    );

    const link = screen.getByRole("link", { name: mockEncounterId });
    expect(link).toHaveAttribute(
      "href",
      `/encounters/encounter.jsp?number=${mockEncounterId}`,
    );
  });

  test("renders image with correct src", () => {
    renderWithProviders(
      <AnnotationSuccessful
        encounterId={mockEncounterId}
        rect={mockRect}
        imageData={mockImageData}
      />,
    );

    const img = screen.getByAltText("placeholder");
    expect(img).toHaveAttribute("src", mockImageData.url);
  });

  test("renders canvas element", () => {
    renderWithProviders(
      <AnnotationSuccessful
        encounterId={mockEncounterId}
        rect={mockRect}
        imageData={mockImageData}
      />,
    );

    const canvas = document.querySelector("canvas");
    expect(canvas).toBeInTheDocument();
  });

  test("does not crash if imageData.url is missing", () => {
    renderWithProviders(
      <AnnotationSuccessful
        encounterId={mockEncounterId}
        rect={mockRect}
        imageData={{ width: 500, height: 500 }}
      />,
    );

    const img = screen.getByAltText("placeholder");
    expect(img).toHaveAttribute("src", "https://via.placeholder.com/150");
  });
});
