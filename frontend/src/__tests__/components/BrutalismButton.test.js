import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import BrutalismButton from "../../components/BrutalismButton";
import { renderWithProviders } from "../../utils/utils";

describe("BrutalismButton", () => {
  const defaultProps = {
    children: "Click Me",
    link: "https://example.com",
  };

  test("renders with default styles and text", () => {
    renderWithProviders(<BrutalismButton {...defaultProps} />);
    const button = screen.getByRole("button");
    const link = screen.getByRole("link", { name: /click me/i });

    expect(button).toBeInTheDocument();
    expect(link).toHaveAttribute("href", defaultProps.link);
    expect(link).toHaveTextContent("Click Me");
  });

  test("does not render arrow icon when noArrow is true", () => {
    renderWithProviders(<BrutalismButton {...defaultProps} noArrow />);
    const arrow = screen.queryByTestId("arrow-icon");
    expect(arrow).not.toBeInTheDocument();
  });

  test("applies hover effect with different box-shadow", () => {
    renderWithProviders(<BrutalismButton {...defaultProps} />);
    const button = screen.getByRole("button");

    fireEvent.mouseEnter(button);
    expect(button).toHaveStyle("box-shadow: 4px 4px 0px #000000");

    fireEvent.mouseLeave(button);
    expect(button).toHaveStyle("box-shadow: 1px 2px 0px #000000");
  });

  test("applies disabled state", () => {
    renderWithProviders(<BrutalismButton {...defaultProps} disabled />);
    const button = screen.getByRole("button");
    expect(button).toBeDisabled();
  });

  test("calls onClick when clicked", () => {
    const onClickMock = jest.fn();
    renderWithProviders(
      <BrutalismButton {...defaultProps} onClick={onClickMock} />,
    );
    const button = screen.getByRole("button");

    fireEvent.click(button);
    expect(onClickMock).toHaveBeenCalled();
  });

  test("applies custom colors and background", () => {
    renderWithProviders(
      <BrutalismButton
        {...defaultProps}
        color="red"
        backgroundColor="blue"
        borderColor="green"
      />,
    );
    const button = screen.getByRole("button");

    expect(button).toHaveStyle("color: red");
    expect(button).toHaveStyle("background: blue");
    expect(button).toHaveStyle("border: 2px solid green");
  });
});
