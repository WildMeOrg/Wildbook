import React from "react";
import Description from "../../../components/Form/Description";
import { renderWithProviders } from "../../../utils/utils";

describe("Description Component", () => {
  it("renders children correctly", () => {
    const { getByText } = renderWithProviders(
      <Description>This is a test description</Description>,
    );
    expect(getByText("This is a test description")).toBeInTheDocument();
  });

  it("applies correct styles", () => {
    const { container } = renderWithProviders(
      <Description>Styled description</Description>,
    );
    const div = container.firstChild;
    expect(div).toHaveStyle("font-size: 14px");
    expect(div).toHaveStyle("font-weight: 400");
    expect(div).toHaveStyle("line-height: 21px");
    expect(div).toHaveStyle("color: #AFB3B7");
    expect(div).toHaveStyle("margin-bottom: 1rem");
  });
});
