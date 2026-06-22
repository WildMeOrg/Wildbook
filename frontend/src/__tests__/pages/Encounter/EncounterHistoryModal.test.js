/* eslint-disable react/display-name */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";

jest.mock("react-bootstrap", () => {
  const Modal = ({ show, onHide, children }) =>
    show ? (
      <div data-testid="modal">
        <button data-testid="modal-close" onClick={onHide}>
          ×
        </button>
        {children}
      </div>
    ) : null;
  Modal.Header = ({ children }) => (
    <div data-testid="modal-header">{children}</div>
  );
  Modal.Title = ({ children }) => (
    <div data-testid="modal-title">{children}</div>
  );
  Modal.Body = ({ children }) => <div data-testid="modal-body">{children}</div>;
  return { Modal };
});

jest.mock("dompurify", () => {
  const sanitize = jest.fn((html) => html);
  return { __esModule: true, default: { sanitize } };
});

import DOMPurify from "dompurify";
import EncounterHistoryModal from "../../../pages/Encounter/EncounterHistoryModal";

const renderModal = (ui) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      {ui}
    </IntlProvider>,
  );

const makeStore = (overrides = {}) => ({
  encounterData: { researcherComments: "" },
  ...overrides,
});

describe("EncounterHistoryModal", () => {
  beforeEach(() => {
    DOMPurify.sanitize.mockClear();
  });

  test("returns null when closed", () => {
    const store = makeStore();
    renderModal(
      <EncounterHistoryModal
        isOpen={false}
        onClose={jest.fn()}
        store={store}
      />,
    );
    expect(screen.queryByTestId("modal")).toBeNull();
  });

  test("shows title and NO_RECORD when no comments", () => {
    const store = makeStore();
    renderModal(
      <EncounterHistoryModal isOpen={true} onClose={jest.fn()} store={store} />,
    );
    expect(screen.getByText("ENCOUNTER_HISTORY")).toBeInTheDocument();
    expect(screen.getByText("NO_RECORD")).toBeInTheDocument();
  });

  test("sanitizes and renders researcherComments", () => {
    const raw = `<p>hello <script>alert(1)</script><em>em</em><a href="x" onclick="evil()">link</a></p>`;
    const cleaned = `<p>hello <em>em</em><a href="x">link</a></p>`;
    DOMPurify.sanitize.mockReturnValueOnce(cleaned);
    const store = makeStore({ encounterData: { researcherComments: raw } });

    const { container } = renderModal(
      <EncounterHistoryModal isOpen={true} onClose={jest.fn()} store={store} />,
    );

    expect(DOMPurify.sanitize).toHaveBeenCalledTimes(1);
    const [, opts] = DOMPurify.sanitize.mock.calls[0];
    expect(opts).toMatchObject({
      ALLOWED_TAGS: ["p", "em", "i", "br", "a"],
      ALLOWED_ATTR: ["href", "title", "target", "data-annot-id"],
    });

    const div = container.querySelector(".encounter-history");
    expect(div).toBeInTheDocument();
    expect(div.innerHTML).toBe(cleaned);
  });

  test("calls onClose when modal close clicked", async () => {
    const user = userEvent.setup();
    const onClose = jest.fn();
    const store = makeStore({
      encounterData: { researcherComments: "<p>x</p>" },
    });

    renderModal(
      <EncounterHistoryModal isOpen={true} onClose={onClose} store={store} />,
    );

    await user.click(screen.getByTestId("modal-close"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
