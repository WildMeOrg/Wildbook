import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import BulkImportInstructionsModal from "../../../pages/BulkImport/BulkImportInstructionsModal";

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: () => ({ data: null }),
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id, defaultMessage, values }) => {
    if (values?.youtubeLink) return values.youtubeLink;
    if (values?.wildbookDocsLink) return values.wildbookDocsLink;
    return <span>{defaultMessage || id}</span>;
  },
}));

describe("BulkImportInstructionsModal (without changing the component)", () => {
  let store;
  beforeEach(() => {
    store = {
      showInstructions: true,
      setShowInstructions: jest.fn(),
    };
  });

  it("renders the Wildbook docs link in the NEED_HELP_DOCS step", () => {
    render(<BulkImportInstructionsModal store={store} />);

    const docsLink = screen.getByRole("link", {
      name: /BULK_IMPORT_INSTRUCTIONS_WILDBOOK_DOCS/,
    });
    expect(docsLink).toHaveAttribute("href", "https://docs.wildme.org/");
    expect(docsLink).toHaveAttribute("target", "_blank");
    expect(docsLink).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("still hides the modal when showInstructions=false", () => {
    store.showInstructions = false;
    render(<BulkImportInstructionsModal store={store} />);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("closes when the Close button is clicked", () => {
    render(<BulkImportInstructionsModal store={store} />);
    fireEvent.click(screen.getByText("BULK_IMPORT_INSTRUCTIONS_CLOSE_BUTTON"));
    expect(store.setShowInstructions).toHaveBeenCalledWith(false);
  });
});
