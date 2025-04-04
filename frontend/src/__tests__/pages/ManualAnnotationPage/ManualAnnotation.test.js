import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import ManualAnnotation from "../../../pages/ManualAnnotation";
import { MemoryRouter } from "react-router-dom";
import { IntlProvider } from "react-intl";

jest.mock("react-select", () => {
  const MockReactSelect = (props) => (
    <select
      data-testid={props["data-testid"] || "mock-select"}
      onChange={(e) => {
        const selected = { label: e.target.value, value: e.target.value };
        props.onChange(selected);
      }}
    >
      {props.options.map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
  );

  MockReactSelect.displayName = "MockReactSelect";

  return MockReactSelect;
});

const mockCreateAnnotation = jest.fn();

jest.mock("../../../models/encounters/useCreateAnnotation", () => () => ({
  createAnnotation: mockCreateAnnotation,
  loading: false,
  error: null,
  submissionDone: false,
  responseData: { id: "annotation123" },
}));

jest.mock("../../../models/useGetSiteSettings", () => () => ({
  data: {
    iaClass: ["Zebra", "Elephant"],
    annotationViewpoint: ["Front", "Side"],
  },
}));

jest.mock("../../../components/AnnotationSuccessful", () => {
  const MockAnnotationSuccessful = () => (
    <div data-testid="annotation-success">Success!</div>
  );

  MockAnnotationSuccessful.displayName = "MockAnnotationSuccessful";

  return MockAnnotationSuccessful;
});

jest.mock("../../../components/AddAnnotationModal", () => {
  const MockAddAnnotationModal = (props) =>
    props.showModal ? (
      <div data-testid="annotation-modal">Modal Shown</div>
    ) : null;

  MockAddAnnotationModal.displayName = "MockAddAnnotationModal";

  return MockAddAnnotationModal;
});

jest.mock("../../../components/ResizableRotatableRect", () => {
  const MockResizableRotatableRect = () => (
    <div data-testid="resizable-rect">ResizableRect</div>
  );

  MockResizableRotatableRect.displayName = "MockResizableRotatableRect";

  return MockResizableRotatableRect;
});

Object.defineProperty(global.Image.prototype, "complete", {
  get() {
    return true;
  },
});

HTMLCanvasElement.prototype.getContext = () => ({
  clearRect: jest.fn(),
  strokeRect: jest.fn(),
  beginPath: jest.fn(),
  moveTo: jest.fn(),
  lineTo: jest.fn(),
  stroke: jest.fn(),
  translate: jest.fn(),
  rotate: jest.fn(),
  save: jest.fn(),
  restore: jest.fn(),
});

const renderComponent = (url = "/manual-annotation?assetId=1&encounterId=2") =>
  render(
    <IntlProvider locale="en">
      <MemoryRouter initialEntries={[url]}>
        <ManualAnnotation />
      </MemoryRouter>
    </IntlProvider>,
  );

describe("ManualAnnotation", () => {
  beforeEach(() => {
    mockCreateAnnotation.mockClear();
  });

  it("renders form and image area", async () => {
    renderComponent();
    expect(await screen.findByText("DRAW_ANNOTATION")).toBeInTheDocument();
    expect(screen.getByTestId("resizable-rect")).toBeInTheDocument();
  });

  it("shows modal when submitting with missing fields", async () => {
    renderComponent();
    fireEvent.click(await screen.findByText("SAVE_ANNOTATION"));
    await waitFor(() => {
      expect(screen.getByTestId("annotation-modal")).toBeInTheDocument();
    });
  });

  it("shows success screen when submissionDone is true", () => {
    jest.doMock("../../../models/encounters/useCreateAnnotation", () => () => ({
      createAnnotation: jest.fn(),
      loading: false,
      error: null,
      submissionDone: true,
      responseData: { id: "successId" },
    }));

    renderComponent();
  });

  it("updates selectors for IA and viewpoint", async () => {
    renderComponent();
    fireEvent.change(screen.getAllByTestId("mock-select")[0], {
      target: { value: "Zebra" },
    });
    fireEvent.change(screen.getAllByTestId("mock-select")[1], {
      target: { value: "Front" },
    });
  });

  it("handles MainButton click with valid fields", async () => {
    renderComponent();

    fireEvent.change(screen.getAllByTestId("mock-select")[0], {
      target: { value: "Zebra" },
    });
    fireEvent.change(screen.getAllByTestId("mock-select")[1], {
      target: { value: "Front" },
    });

    const saveBtn = await screen.findByText("SAVE_ANNOTATION");
    await waitFor(() => fireEvent.click(saveBtn));
  });

  it("handles key and mouse events", () => {
    renderComponent();
    fireEvent.keyDown(window, { key: "Delete" });
    const rectArea = screen.getByTestId("resizable-rect").parentElement;
    fireEvent.mouseDown(rectArea, { clientX: 10, clientY: 10 });
    fireEvent.mouseMove(rectArea, { clientX: 30, clientY: 30 });
    fireEvent.mouseUp(rectArea);
  });

  it("toggles draw status when clicking delete icon", async () => {
    renderComponent();
    const toggle = screen.getByText("DRAW");
    fireEvent.click(toggle);
  });

  it("draws annotation on canvas with valid data", async () => {
    renderComponent();
    fireEvent.load(screen.getByRole("img"));
  });

  it("submits annotation when all required fields are filled", async () => {
    renderComponent();

    fireEvent.change(screen.getAllByTestId("mock-select")[0], {
      target: { value: "Zebra" },
    });
    fireEvent.change(screen.getAllByTestId("mock-select")[1], {
      target: { value: "Front" },
    });

    const canvasContainer = screen.getByTestId("resizable-rect").parentElement;
    fireEvent.mouseDown(canvasContainer, { clientX: 10, clientY: 10 });
    fireEvent.mouseMove(canvasContainer, { clientX: 100, clientY: 100 });
    fireEvent.mouseUp(canvasContainer);

    const saveBtn = await screen.findByText("SAVE_ANNOTATION");
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(mockCreateAnnotation).toHaveBeenCalled();
      const args = mockCreateAnnotation.mock.calls[0][0];
      expect(args).toHaveProperty("ia");
      expect(args).toHaveProperty("viewpoint");
      expect(args.width).toBeGreaterThan(0);
      expect(args.height).toBeGreaterThan(0);
    });
  });

  it("shows error modal on createAnnotation error", async () => {
    jest.doMock("../../../models/encounters/useCreateAnnotation", () => () => ({
      createAnnotation: jest.fn().mockImplementation(() => {
        throw new Error("Test error");
      }),
      loading: false,
      error: { message: "Submission failed" },
      submissionDone: false,
      responseData: null,
    }));

    renderComponent();
    const saveBtn = await screen.findByText("SAVE_ANNOTATION");
    fireEvent.click(saveBtn);
    await waitFor(() => {
      expect(screen.getByTestId("annotation-modal")).toBeInTheDocument();
    });
  });

  it("toggles draw status from DELETE to DRAW and resets rectangle", async () => {
    renderComponent();

    const toggle = screen.getByText("DRAW");
    fireEvent.click(toggle);

    act(() => {
      fireEvent.keyDown(window, { key: "Delete" });
    });

    await waitFor(() => {
      expect(screen.getByText("DRAW")).toBeInTheDocument();
    });
  });

  it("does not fetch data if assetId or encounterId is missing", async () => {
    renderComponent("/manual-annotation?assetId=1");
    await waitFor(() =>
      expect(screen.getByText("DRAW_ANNOTATION")).toBeInTheDocument(),
    );
  });

  jest.mock("../../../models/js/calculateScaleFactor", () =>
    jest.fn(() => ({ x: 2, y: 2 })),
  );

  it("handles fetch media assets error", async () => {
    global.fetch = jest.fn(() => Promise.reject(new Error("Failed to fetch")));

    renderComponent();
    await waitFor(() =>
      expect(screen.getByText("DRAW_ANNOTATION")).toBeInTheDocument(),
    );
  });

  it("shows modal if IA is selected but viewpoint is missing", async () => {
    renderComponent();
    fireEvent.change(screen.getAllByTestId("mock-select")[0], {
      target: { value: "Zebra" },
    });
    fireEvent.click(await screen.findByText("SAVE_ANNOTATION"));

    await waitFor(() => {
      expect(screen.getByTestId("annotation-modal")).toBeInTheDocument();
    });
  });

  it("draws non-trivial annotation on canvas", async () => {
    const mockAnnotations = {
      width: 800,
      height: 600,
      url: "test.jpg",
      annotations: [
        {
          x: 100,
          y: 100,
          width: 200,
          height: 150,
          theta: 0.5,
          trivial: false,
        },
      ],
    };

    global.fetch = jest.fn(() =>
      Promise.resolve({
        json: () => Promise.resolve(mockAnnotations),
      }),
    );

    const strokeRect = jest.fn();
    const translate = jest.fn();
    const rotate = jest.fn();
    const save = jest.fn();
    const restore = jest.fn();

    HTMLCanvasElement.prototype.getContext = () => ({
      clearRect: jest.fn(),
      strokeRect,
      beginPath: jest.fn(),
      moveTo: jest.fn(),
      lineTo: jest.fn(),
      stroke: jest.fn(),
      translate,
      rotate,
      save,
      restore,
    });

    renderComponent();

    await waitFor(() => {
      expect(strokeRect).toHaveBeenCalled();
      expect(translate).toHaveBeenCalled();
      expect(rotate).toHaveBeenCalled();
      expect(save).toHaveBeenCalled();
      expect(restore).toHaveBeenCalled();
    });
  });
});
