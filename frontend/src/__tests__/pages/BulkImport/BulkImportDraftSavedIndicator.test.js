import React from "react";
import { render, screen } from "@testing-library/react";
import DraftSaveIndicator from "../../../pages/BulkImport/BulkImportDraftSavedIndicator";

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

describe("DraftSaveIndicator", () => {
  test("shows saving draft message when isSavingDraft is true", () => {
    const store = { isSavingDraft: true, lastSavedAt: null };
    render(<DraftSaveIndicator store={store} />);
    expect(screen.getByText("BULK_IMPORT_SAVING_AS_DRAFT")).toBeInTheDocument();
  });

  test("shows saved‑as‑draft indicator when lastSavedAt is set", () => {
    const store = { isSavingDraft: false, lastSavedAt: Date.now() };
    const { container } = render(<DraftSaveIndicator store={store} />);

    expect(
      container.querySelector(".bi.bi-check-circle-fill"),
    ).toBeInTheDocument();

    expect(screen.getByText("BULK_IMPORT_SAVED_AS_DRAFT")).toBeInTheDocument();
  });

  test("renders nothing when not saving and no lastSavedAt", () => {
    const store = { isSavingDraft: false, lastSavedAt: null };
    const { container } = render(<DraftSaveIndicator store={store} />);
    expect(container).toBeEmptyDOMElement();
  });
});
