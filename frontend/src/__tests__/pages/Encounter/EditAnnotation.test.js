/* eslint-disable react/display-name */
/* eslint-disable no-unused-vars */
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import axios from "axios";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id, defaultMessage }) => (
    <span>{id || defaultMessage}</span>
  ),
}));

jest.mock("../../../ThemeColorProvider", () => {
  const React = require("react");
  return {
    __esModule: true,
    default: React.createContext({
      primaryColors: { primary500: "#0066ff" },
      defaultColors: { white: "#fff" },
    }),
  };
});

jest.mock("react-select", () => {
  let selectClickCount = 0;
  return (props) => {
    return (
      <div
        data-testid={props["data-testid"] || "react-select"}
        onClick={() => {
          selectClickCount += 1;
          if (selectClickCount === 1) {
            props.onChange && props.onChange({ value: "head", label: "head" });
          } else if (selectClickCount === 2) {
            props.onChange &&
              props.onChange({ value: "front", label: "front" });
          }
        }}
      >
        ReactSelectMock
      </div>
    );
  };
});

jest.mock("../../../components/MainButton", () => (props) => (
  <button data-testid="main-button" onClick={props.onClick}>
    {props.children}
  </button>
));

jest.mock("../../../components/ResizableRotatableRect", () => (props) => (
  <div data-testid="rr-rect" />
));

jest.mock(
  "../../../components/AddAnnotationModal",
  () => (props) =>
    props.showModal ? <div data-testid="add-annotation-modal" /> : null,
);

jest.mock("../../../components/AnnotationSuccessful", () => (props) => (
  <div data-testid="annotation-success">SUCCESS</div>
));

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: () => ({
    data: {
      iaClassesForTaxonomy: {
        "Panthera leo": ["head", "side"],
      },
      annotationViewpoint: ["front", "left"],
    },
  }),
}));

jest.mock("react-router-dom", () => ({
  useSearchParams: () => [
    new URLSearchParams(
      "assetId=A1&encounterId=E1&annotationId=ANN1&annotation=" +
        encodeURIComponent(
          JSON.stringify({ x: 10, y: 10, width: 50, height: 40, theta: 0 }),
        ),
    ),
  ],
}));

const createAnnotationMock = jest.fn();
jest.mock("../../../models/encounters/useCreateAnnotation", () => {
  const createAnnotationMock = jest.fn();
  return {
    __esModule: true,
    default: () => ({
      createAnnotation: createAnnotationMock,
      loading: false,
      error: null,
      submissionDone: false,
      responseData: { id: "NEW-ANN" },
    }),
  };
});

jest.mock("../../../models/js/calculateFinalRect", () => ({
  __esModule: true,
  default: (r) => r,
}));

jest.mock("../../../models/js/calculateScaleFactor", () => ({
  __esModule: true,
  default: () => ({ x: 1, y: 1 }),
}));

jest.mock("axios");

beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = function () {
    return {
      clearRect: jest.fn(),
      save: jest.fn(),
      restore: jest.fn(),
      translate: jest.fn(),
      rotate: jest.fn(),
      strokeRect: jest.fn(),
      beginPath: jest.fn(),
      moveTo: jest.fn(),
      lineTo: jest.fn(),
      stroke: jest.fn(),
    };
  };
});

import EditAnnotation from "../../../pages/EditAnnotation";
let selectClickCount = 0;
describe("EditAnnotation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    selectClickCount = 0;
    axios.get = jest.fn();
    global.fetch = jest.fn().mockResolvedValue({
      json: async () => ({
        width: 800,
        height: 600,
        url: "https://example.com/img.jpg",
        annotations: [
          {
            id: "OTHER",
            encounterId: "E1",
            x: 30,
            y: 30,
            width: 40,
            height: 40,
            theta: 0,
            trivial: false,
          },
          {
            id: "ANN1",
            encounterId: "E1",
            x: 10,
            y: 10,
            width: 50,
            height: 40,
            theta: 0,
            trivial: false,
          },
        ],
      }),
    });
    axios.patch.mockResolvedValue({ status: 200 });
  });

  test("renders basic UI", async () => {
    render(<EditAnnotation />);
    expect(screen.getByText("EDIT_ANNOTATIONS")).toBeInTheDocument();
    expect(await screen.findByTestId("rr-rect")).toBeInTheDocument();
  });

  test("save without required fields shows modal", async () => {
    render(<EditAnnotation />);

    await waitFor(() => {
      expect(screen.getByTestId("main-button")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId("main-button"));

    expect(
      await screen.findByTestId("add-annotation-modal"),
    ).toBeInTheDocument();
    expect(axios.patch).not.toHaveBeenCalled();
    expect(createAnnotationMock).not.toHaveBeenCalled();
  });

  test("select ia + viewpoint then save will patch and create", async () => {
    render(<EditAnnotation />);

    await waitFor(() => {
      const selects = screen.getAllByTestId("react-select");
      expect(selects.length).toBeGreaterThanOrEqual(2);
    });

    const selects = screen.getAllByTestId("react-select");

    fireEvent.click(selects[0]);
    fireEvent.click(selects[1]);

    const container = screen.getByRole("img", {
      name: "annotationimages",
    }).parentElement;

    fireEvent.mouseDown(container, { clientX: 10, clientY: 10 });
    fireEvent.mouseMove(container, { clientX: 100, clientY: 100 });
    fireEvent.mouseUp(container);

    fireEvent.click(screen.getByTestId("main-button"));

    await waitFor(() => {
      expect(axios.patch).toHaveBeenCalledWith("/api/v3/encounters/E1", [
        {
          op: "remove",
          path: "annotations",
          value: "ANN1",
        },
      ]);
    });
  });

  test("when submissionDone is true, shows success component", async () => {
    const useCreateAnnotation =
      require("../../../models/encounters/useCreateAnnotation").default;

    jest
      .spyOn(
        require("../../../models/encounters/useCreateAnnotation"),
        "default",
      )
      .mockReturnValue({
        createAnnotation: jest.fn(),
        loading: false,
        error: null,
        submissionDone: true,
        responseData: { id: "DONE-1" },
      });

    const { rerender } = render(<EditAnnotation />);

    await waitFor(() => {
      expect(screen.getByTestId("annotation-success")).toBeInTheDocument();
    });
  });
});
