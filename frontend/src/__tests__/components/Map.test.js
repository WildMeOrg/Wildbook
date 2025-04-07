import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import MapComponent from "../../components/Map";
import useGetSiteSettings from "../../models/useGetSiteSettings";

jest.mock("../../models/useGetSiteSettings", () => jest.fn());

jest.mock("react-intl", () => {
  const OriginalModule = jest.requireActual("react-intl");
  return {
    ...OriginalModule,
    FormattedMessage: ({ id }) => <span>{id}</span>,
  };
});

jest.mock("../../ThemeColorProvider", () => {
  const React = require("react");
  return {
    __esModule: true,
    default: React.createContext({
      primaryColors: {
        primary700: "#000",
      },
    }),
  };
});

describe("MapComponent", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders loading state when no googleMapsKey is present", () => {
    useGetSiteSettings.mockReturnValue({ data: {} });

    render(<MapComponent setBounds={jest.fn()} />);

    expect(screen.getByText(/MAP_IS_LOADING/i)).toBeInTheDocument();
  });

  test("renders the Draw button", () => {
    useGetSiteSettings.mockReturnValue({ data: {} });

    render(<MapComponent setBounds={jest.fn()} />);
    expect(screen.getByRole("button")).toBeInTheDocument();
  });

  test("toggles button text between DRAW and CANCEL", () => {
    useGetSiteSettings.mockReturnValue({ data: {} });

    render(<MapComponent setBounds={jest.fn()} />);
    const button = screen.getByRole("button");

    expect(button.textContent.toUpperCase()).toBe("DRAW");

    fireEvent.click(button);
    expect(button.textContent.toUpperCase()).toBe("CANCEL");

    fireEvent.click(button);
    expect(button.textContent.toUpperCase()).toBe("DRAW");
  });

  test("calls setBounds and setTempBounds when drawing is toggled", () => {
    const setBoundsMock = jest.fn();
    const setTempBoundsMock = jest.fn();
    useGetSiteSettings.mockReturnValue({ data: {} });

    render(
      <MapComponent
        setBounds={setBoundsMock}
        setTempBounds={setTempBoundsMock}
      />,
    );

    const button = screen.getByRole("button");
    fireEvent.click(button);

    expect(setBoundsMock).toHaveBeenCalledWith(null);
    expect(setTempBoundsMock).toHaveBeenCalledWith(null);
  });
});
