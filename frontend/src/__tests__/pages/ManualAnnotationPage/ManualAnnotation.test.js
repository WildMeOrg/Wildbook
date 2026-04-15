/* eslint-disable react/display-name */
import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { IntlProvider } from "react-intl";
import ManualAnnotation from "../../../pages/ManualAnnotation";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../ThemeColorProvider", () => {
  const React = require("react");
  return React.createContext({
    primaryColors: { primary500: "#1677ff" },
    defaultColors: { white: "#fff" },
  });
});

jest.mock("../../../components/MainButton", () => (props) => (
  <button onClick={props.onClick} disabled={props.loading}>
    {props.children}
  </button>
));

jest.mock("react-select", () => {
  const MockReactSelect = (props) => {
    const options = props.options || [];
    return (
      <select
        data-testid="mock-select"
        value={props.value?.value || ""}
        onChange={(e) => {
          const selected = options.find((o) => o.value === e.target.value) || {
            value: e.target.value,
            label: e.target.value,
          };
          props.onChange?.(selected);
        }}
      >
        <option value="">--</option>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    );
  };
  MockReactSelect.displayName = "MockReactSelect";
  return MockReactSelect;
});

const mockCreateAnnotation = jest.fn();

const createHookState = {
  loading: false,
  error: null,
  submissionDone: false,
  responseData: { id: "annotation123" },
};

jest.mock("../../../models/encounters/useCreateAnnotation", () => () => ({
  createAnnotation: mockCreateAnnotation,
  loading: createHookState.loading,
  error: createHookState.error,
  submissionDone: createHookState.submissionDone,
  responseData: createHookState.responseData,
}));

const siteSettingsState = {
  iaClassesForTaxonomy: { testTaxonomy: ["Zebra", "Elephant"] },
  annotationViewpoint: ["Front", "Side"],
};
jest.mock("../../../models/useGetSiteSettings", () => () => ({
  data: siteSettingsState,
}));

let latestAnnotationSuccessfulProps;
jest.mock("../../../components/AnnotationSuccessful", () => {
  const Mock = (props) => {
    latestAnnotationSuccessfulProps = props;
    return <div data-testid="annotation-success">Success!</div>;
  };
  Mock.displayName = "MockAnnotationSuccessful";
  return Mock;
});

jest.mock("../../../components/AddAnnotationModal", () => {
  const Mock = (props) =>
    props.showModal ? <div data-testid="annotation-modal">Modal</div> : null;
  Mock.displayName = "MockAddAnnotationModal";
  return Mock;
});

jest.mock("../../../components/ResizableRotatableRect", () => {
  const Mock = (props) => (
    <div data-testid="resizable-rect">
      <button
        data-testid="set-valid-rect"
        onClick={() =>
          props.setRect({
            x: 10,
            y: 20,
            width: 100,
            height: 80,
            rotation: 0,
          })
        }
      >
        set-rect
      </button>
      <button
        data-testid="set-negative-rect"
        onClick={() =>
          props.setRect({
            x: 50,
            y: 60,
            width: -20,
            height: -10,
            rotation: 0,
          })
        }
      >
        set-negative
      </button>
      <button data-testid="set-rotation" onClick={() => props.setValue?.(30)}>
        set-rotation
      </button>
    </div>
  );
  Mock.displayName = "MockResizableRotatableRect";
  return Mock;
});

const mockCalculateScaleFactor = jest.fn(() => ({ x: 1, y: 1 }));
jest.mock("../../../models/js/calculateScaleFactor", () => ({
  __esModule: true,
  default: (...args) => mockCalculateScaleFactor(...args),
}));

const mockCalculateFinalRect = jest.fn((rect) => ({
  x: rect.x,
  y: rect.y,
  width: rect.width,
  height: rect.height,
  rotation: rect.rotation || 0,
}));
jest.mock("../../../models/js/calculateFinalRect", () => ({
  __esModule: true,
  default: (...args) => mockCalculateFinalRect(...args),
}));

Object.defineProperty(global.Image.prototype, "complete", {
  get() {
    return true;
  },
});

const ctx = {
  clearRect: jest.fn(),
  strokeRect: jest.fn(),
  translate: jest.fn(),
  rotate: jest.fn(),
  save: jest.fn(),
  restore: jest.fn(),
  get strokeStyle() {
    return this._strokeStyle;
  },
  set strokeStyle(v) {
    this._strokeStyle = v;
  },
  get lineWidth() {
    return this._lineWidth;
  },
  set lineWidth(v) {
    this._lineWidth = v;
  },
};
HTMLCanvasElement.prototype.getContext = jest.fn(() => ctx);

const defaultFetchPayload = {
  width: 800,
  height: 600,
  url: "test.jpg",
  rotationInfo: null,
  annotations: [
    {
      encounterId: "2",
      encounterTaxonomy: "testTaxonomy",
      x: 10,
      y: 10,
      width: 20,
      height: 20,
      theta: 0.3,
      trivial: false,
    },
    {
      encounterId: "2",
      encounterTaxonomy: "testTaxonomy",
      x: 1,
      y: 1,
      width: 2,
      height: 2,
      theta: 0,
      trivial: true,
    },
  ],
};

const mockFetchWith = (payload = defaultFetchPayload) => {
  global.fetch = jest.fn(() =>
    Promise.resolve({
      json: () => Promise.resolve(payload),
    }),
  );
};

const renderComponent = (url = "/manual-annotation?assetId=1&encounterId=2") =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <MemoryRouter initialEntries={[url]}>
        <ManualAnnotation />
      </MemoryRouter>
    </IntlProvider>,
  );

describe("ManualAnnotation (important coverage)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockCreateAnnotation.mockClear();
    mockFetchWith();
    mockCalculateScaleFactor.mockClear();
    mockCalculateFinalRect.mockClear();
    latestAnnotationSuccessfulProps = undefined;

    createHookState.loading = false;
    createHookState.error = null;
    createHookState.submissionDone = false;
    createHookState.responseData = { id: "annotation123" };

    siteSettingsState.iaClassesForTaxonomy = {
      testTaxonomy: ["Zebra", "Elephant"],
    };
    siteSettingsState.annotationViewpoint = ["Front", "Side"];

    global.alert = jest.fn();
  });

  test("fetches media asset and renders form and drawing area", async () => {
    renderComponent();

    expect(await screen.findByText("DRAW_ANNOTATION")).toBeInTheDocument();
    expect(global.fetch).toHaveBeenCalledWith("/api/v3/media-assets/1");

    expect(screen.getByText("SAVE_ANNOTATION")).toBeInTheDocument();
    expect(screen.getByTestId("resizable-rect")).toBeInTheDocument();
    expect(screen.getAllByTestId("mock-select")).toHaveLength(2);
  });

  test("draw-existing-annotations effect draws only non-trivial annotations on canvas", async () => {
    renderComponent();

    await screen.findByText("DRAW_ANNOTATION");

    await waitFor(() => {
      expect(ctx.strokeRect).toHaveBeenCalled();
    });

    expect(ctx.strokeRect).toHaveBeenCalledTimes(1);

    expect(ctx.save).toHaveBeenCalled();
    expect(ctx.restore).toHaveBeenCalled();
    expect(ctx.rotate).toHaveBeenCalled();
    expect(ctx.translate).toHaveBeenCalled();
  });

  test("shows NO_TAXONOMY when no taxonomy found for encounter", async () => {
    mockFetchWith({
      ...defaultFetchPayload,
      annotations: [
        {
          encounterId: "2",
          x: 10,
          y: 10,
          width: 20,
          height: 20,
          theta: 0,
          trivial: false,
        },
      ],
    });

    renderComponent();

    expect(await screen.findByText("DRAW_ANNOTATION")).toBeInTheDocument();
    expect(await screen.findByText("NO_TAXONOMY")).toBeInTheDocument();
  });

  test("shows NO_IA_CLASS when taxonomy exists but has no IA classes", async () => {
    siteSettingsState.iaClassesForTaxonomy = {};
    mockFetchWith({
      ...defaultFetchPayload,
      annotations: [
        {
          encounterId: "2",
          encounterTaxonomy: "unknownTaxonomy",
          x: 10,
          y: 10,
          width: 20,
          height: 20,
          theta: 0,
          trivial: false,
        },
      ],
    });

    renderComponent();

    expect(await screen.findByText("DRAW_ANNOTATION")).toBeInTheDocument();
    expect(await screen.findByText("NO_IA_CLASS")).toBeInTheDocument();
  });

  test("shows modal when saving with missing required fields", async () => {
    renderComponent();

    fireEvent.click(await screen.findByText("SAVE_ANNOTATION"));

    await waitFor(() => {
      expect(screen.getByTestId("annotation-modal")).toBeInTheDocument();
    });

    expect(mockCreateAnnotation).not.toHaveBeenCalled();
  });

  test("submits annotation when IA, viewpoint and rect are provided", async () => {
    renderComponent();

    await screen.findByText("DRAW_ANNOTATION");

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Zebra" })).toBeInTheDocument();
    });

    const [iaSelect, viewpointSelect] = screen.getAllByTestId("mock-select");

    fireEvent.change(iaSelect, { target: { value: "Zebra" } });

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Front" })).toBeInTheDocument();
    });
    fireEvent.change(viewpointSelect, { target: { value: "Front" } });

    fireEvent.click(screen.getByTestId("set-valid-rect"));
    fireEvent.click(screen.getByText("SAVE_ANNOTATION"));

    await waitFor(() => {
      expect(mockCreateAnnotation).toHaveBeenCalledTimes(1);
    });

    const payload = mockCreateAnnotation.mock.calls[0][0];
    expect(payload.encounterId).toBe("2");
    expect(payload.assetId).toBe("1");
    expect(payload.ia).toEqual({ value: "Zebra", label: "Zebra" });
    expect(payload.viewpoint).toEqual({ value: "Front", label: "Front" });
    expect(payload.width).toBeGreaterThan(0);
    expect(payload.height).toBeGreaterThan(0);
  });

  test("applies rotationInfo adjustment to x/y/width/height before submitting", async () => {
    mockFetchWith({
      ...defaultFetchPayload,
      rotationInfo: { any: "truthy" },
      width: 800,
      height: 600,
    });

    renderComponent();
    await screen.findByText("DRAW_ANNOTATION");

    const [iaSelect, viewpointSelect] = screen.getAllByTestId("mock-select");
    fireEvent.change(iaSelect, { target: { value: "Zebra" } });
    fireEvent.change(viewpointSelect, { target: { value: "Front" } });
    fireEvent.click(screen.getByTestId("set-valid-rect"));

    fireEvent.click(screen.getByText("SAVE_ANNOTATION"));

    await waitFor(() => expect(mockCreateAnnotation).toHaveBeenCalledTimes(1));

    const payload = mockCreateAnnotation.mock.calls[0][0];

    expect(payload.x).toBeCloseTo(10 * 0.75, 5);
    expect(payload.width).toBeCloseTo(100 * 0.75, 5);
    expect(payload.y).toBeCloseTo(20 * (800 / 600), 5);
    expect(payload.height).toBeCloseTo(80 * (800 / 600), 5);
  });

  test("when hook error becomes truthy, modal is shown", async () => {
    createHookState.error = new Error("server error");

    renderComponent();
    await screen.findByText("DRAW_ANNOTATION");

    expect(await screen.findByTestId("annotation-modal")).toBeInTheDocument();
  });

  test("when submissionDone is true, renders AnnotationSuccessful with key props", async () => {
    createHookState.submissionDone = true;

    renderComponent();

    expect(await screen.findByTestId("annotation-success")).toBeInTheDocument();

    expect(latestAnnotationSuccessfulProps).toBeTruthy();
    expect(latestAnnotationSuccessfulProps.annotationId).toBe("annotation123");
    expect(latestAnnotationSuccessfulProps.encounterId).toBe("2");

    expect(latestAnnotationSuccessfulProps.rect).toEqual(
      expect.objectContaining({
        x: expect.any(Number),
        y: expect.any(Number),
        width: expect.any(Number),
        height: expect.any(Number),
      }),
    );

    expect(latestAnnotationSuccessfulProps.imageData).toEqual(
      expect.objectContaining({
        width: expect.any(Number),
        height: expect.any(Number),
        url: expect.any(String),
        annotations: expect.any(Array),
      }),
    );
  });

  test("fetch failure triggers alert and loadingIa ends (still renders page)", async () => {
    global.fetch = jest.fn(() => Promise.reject(new Error("network")));
    renderComponent();

    expect(await screen.findByText("DRAW_ANNOTATION")).toBeInTheDocument();
    await waitFor(() => {
      expect(global.alert).toHaveBeenCalled();
    });
  });

  test("delete key clears rect; subsequent save is considered incomplete (modal shown)", async () => {
    renderComponent();
    await screen.findByText("DRAW_ANNOTATION");

    const [iaSelect, viewpointSelect] = screen.getAllByTestId("mock-select");
    fireEvent.change(iaSelect, { target: { value: "Zebra" } });
    fireEvent.change(viewpointSelect, { target: { value: "Front" } });

    fireEvent.click(screen.getByTestId("set-valid-rect"));

    act(() => {
      window.dispatchEvent(new KeyboardEvent("keydown", { key: "Delete" }));
    });

    fireEvent.click(screen.getByText("SAVE_ANNOTATION"));

    await waitFor(() => {
      expect(screen.getByTestId("annotation-modal")).toBeInTheDocument();
    });
    expect(mockCreateAnnotation).not.toHaveBeenCalled();
  });
});
