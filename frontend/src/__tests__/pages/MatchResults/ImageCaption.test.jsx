import React from "react";
import { render, screen } from "@testing-library/react";
import ImageCaption from "../../../pages/MatchResultsPage/components/ImageCaption";

const props = {
  date: "2000-02-07",
  filename: "whale_left_flank.jpg",
  locationId: "Western Australia",
  testId: "cap",
};

describe("ImageCaption placement", () => {
  test("defaults to an overlay pinned to the bottom of the image (match-results)", () => {
    render(<ImageCaption {...props} />);
    expect(screen.getByTestId("cap")).toHaveStyle({
      position: "absolute",
      left: "0px",
      right: "0px",
      bottom: "0px",
      pointerEvents: "none",
      boxSizing: "border-box",
    });
  });

  test("below places the strip in normal flow at its parent's width", () => {
    render(<ImageCaption {...props} placement="below" />);
    const strip = screen.getByTestId("cap");
    expect(strip.style.position).toBe("");
    expect(strip.style.bottom).toBe("");
    expect(strip.style.pointerEvents).toBe("");
    expect(strip).toHaveStyle({
      width: "0px",
      minWidth: "100%",
      boxSizing: "border-box",
    });
  });

  test("both placements keep the same strip styling and values", () => {
    for (const placement of ["overlay", "below"]) {
      const { unmount } = render(
        <ImageCaption {...props} placement={placement} />,
      );
      expect(screen.getByTestId("cap")).toHaveStyle({
        background: "rgba(17, 17, 17, 0.78)",
        color: "#fff",
        fontSize: "13px",
      });
      expect(screen.getByTestId("cap-filename")).toHaveTextContent(
        "whale_left_flank.jpg",
      );
      expect(screen.getByTestId("cap-location")).toHaveTextContent(
        "Western Australia",
      );
      unmount();
    }
  });
});
