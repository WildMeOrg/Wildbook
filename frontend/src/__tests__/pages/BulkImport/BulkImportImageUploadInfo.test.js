import React from "react";
import { render, screen } from "@testing-library/react";
import { BulkImportImageUploadInfo } from "../../../pages/BulkImport/BulkImportImageUploadInfo";
import ThemeColorContext from "../../../ThemeColorProvider";

jest.mock("react-intl", () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }, values) =>
      values ? defaultMessage.replace("{count}", values.count) : defaultMessage,
  }),
}));

jest.mock("react-icons/fa", () => ({
  FaImage: (props) => <svg data-testid="fa-image" {...props} />,
}));

jest.mock("../../../components/InfoAccordion", () => {
  const component = (props) => (
    <div
      data-testid="info-accordion"
      data-expanded={props.expanded}
      data-title={props.title}
      data-data={JSON.stringify(props.data)}
    >
      {props.icon}
    </div>
  );
  component.displayName = "InfoAccordion";
  return component;
});

describe("BulkImportImageUploadInfo", () => {
  const store = {
    missingPhotos: ["a", "b"],
    failedImages: [],
    uploadedImages: ["x", "y", "z"],
  };

  it("renders InfoAccordion with the correct title, data and icon when expanded=true", () => {
    const theme = { primaryColors: { primary500: "#123456" } };

    render(
      <ThemeColorContext.Provider value={theme}>
        <BulkImportImageUploadInfo store={store} expanded={true} />
      </ThemeColorContext.Provider>,
    );

    const accordion = screen.getByTestId("info-accordion");

    expect(accordion).toHaveAttribute("data-expanded", "true");

    expect(accordion).toHaveAttribute("data-title", "photos uploaded: 3");

    const data = JSON.parse(accordion.getAttribute("data-data"));
    expect(data).toEqual([
      { label: "photos missing", value: 2 },
      { label: "photos failed", value: 0 },
      { label: "photos uploaded", value: 3 },
    ]);

    expect(screen.getByTestId("fa-image")).toBeInTheDocument();
  });

  it("passes expanded=false correctly", () => {
    const theme = { primaryColors: { primary500: "#abcdef" } };

    render(
      <ThemeColorContext.Provider value={theme}>
        <BulkImportImageUploadInfo store={store} expanded={false} />
      </ThemeColorContext.Provider>,
    );

    expect(screen.getByTestId("info-accordion")).toHaveAttribute(
      "data-expanded",
      "false",
    );
  });
});
