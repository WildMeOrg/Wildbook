/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("swiper/css", () => ({}), { virtual: true });

jest.mock(
  "swiper/react",
  () => {
    const React = require("react");
    return {
      Swiper: ({ children, onSwiper }) => {
        React.useEffect(() => {
          if (typeof onSwiper === "function") {
            onSwiper({ slideTo: jest.fn(), destroyed: false });
          }
        }, [onSwiper]);
        return <div data-testid="swiper">{children}</div>;
      },
      SwiperSlide: ({ children }) => (
        <div data-testid="swiper-slide">{children}</div>
      ),
    };
  },
  { virtual: true },
);

jest.mock("../../../components/MainButton", () => (props) => (
  <button
    data-testid={props["data-testid"] || "main-button"}
    onClick={props.onClick}
    disabled={props.disabled}
  >
    {props.children}
  </button>
));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
  useIntl: () => ({
    formatMessage: ({ id, defaultMessage }) => defaultMessage || id,
  }),
}));

jest.mock(
  "../../../components/ToolTip",
  () => (props) =>
    props.show ? (
      <div data-testid="tooltip">
        {props.children} ({props.x},{props.y})
      </div>
    ) : null,
);

jest.mock("../../../components/PillWithButton", () => (props) => (
  <div>
    <span>{props.text}</span>
    <button onClick={props.onClose}>x</button>
  </div>
));

jest.mock("../../../utils/keywordsFunctions", () => ({
  addExistingKeyword: jest.fn(async () => ({ success: true })),
  addNewKeywordText: jest.fn(async () => ({ success: true })),
  removeKeyword: jest.fn(async () => ({ success: true })),
  addExistingLabeledKeyword: jest.fn(async () => ({ success: true })),
}));

jest.mock("../../../ThemeColorProvider", () => {
  const React = require("react");
  return {
    __esModule: true,
    default: React.createContext({
      wildMeColors: { cyan700: "#00abc2" },
      statusColors: { red500: "#ff0000" },
    }),
  };
});

import ImageModal from "../../../components/ImageModal";

const makeImageStore = (overrides = {}) => ({
  access: "write",
  showAnnotations: true,
  setShowAnnotations: jest.fn(),
  encounterData: {
    id: "E-1",
    individualDisplayName: "Dolphin-1",
    date: "2025-10-30",
    mediaAssets: [
      { id: "ma-1", url: "https://img/1.jpg", width: 800, height: 600 },
      { id: "ma-2", url: "https://img/2.jpg", width: 800, height: 600 },
    ],
  },
  selectedImageIndex: 0,
  setOpenMatchCriteriaModal: jest.fn(),
  setSelectedAnnotationId: jest.fn(),
  selectedAnnotationId: null,
  matchResultClickable: true,
  hasMatchableAnnotations: true,
  encounterAnnotations: [{ id: "ann-1", iaTaskId: "task-123" }],
  tags: [],
  addTagsFieldOpen: false,
  selectedKeyword: null,
  selectedLabeledKeyword: null,
  selectedAllowedValues: null,
  availableKeywords: [],
  availableKeywordsId: [],
  availabelLabeledKeywords: [],
  labeledKeywordAllowedValues: [],
  deleteImage: jest.fn(async () => {}),
  removeAnnotation: jest.fn(async () => {}),
  refreshEncounterData: jest.fn(async () => {}),
  setAddTagsFieldOpen: jest.fn(),
  setSelectedKeyword: jest.fn(),
  setSelectedLabeledKeyword: jest.fn(),
  setSelectedAllowedValues: jest.fn(),
  ...overrides,
});

const assets = [
  { id: "ma-1", url: "https://img/1.jpg", width: 800, height: 600 },
  { id: "ma-2", url: "https://img/2.jpg", width: 800, height: 600 },
];

const rects = [
  {
    x: 10,
    y: 20,
    width: 100,
    height: 120,
    rotation: 0,
    annotationId: "ann-1",
    encounterId: "E-1",
    viewpoint: "left",
    iaClass: "dolphin",
  },
];

const renderModal = (props = {}) => {
  const defaultStore = makeImageStore();
  return render(
    <ImageModal
      onClose={jest.fn()}
      assets={assets}
      index={0}
      setIndex={jest.fn()}
      rects={rects}
      imageStore={defaultStore}
      {...props}
    />,
  );
};

describe("ImageModal", () => {
  let openSpy;
  let confirmSpy;

  beforeEach(() => {
    jest.clearAllMocks();
    openSpy = jest.spyOn(window, "open").mockImplementation(() => null);
    confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(true);
  });

  afterEach(() => {
    openSpy.mockRestore();
    confirmSpy.mockRestore();
  });

  test("renders modal, main image, thumbnails, and danger button", () => {
    renderModal();

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByAltText("asset-ma-1")).toBeInTheDocument();
    expect(screen.getAllByTestId("swiper-slide")).toHaveLength(2);
    expect(screen.getByText("DELETE_IMAGE")).toBeInTheDocument();
  });

  test("close button calls onClose", () => {
    const onClose = jest.fn();
    renderModal({ onClose });

    fireEvent.click(screen.getByLabelText("Close"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  test("next button calls setIndex with next index", () => {
    const setIndex = jest.fn();
    renderModal({ setIndex });

    fireEvent.click(screen.getByLabelText("Next image"));
    expect(setIndex).toHaveBeenCalledWith(1);
  });

  test("toggle show annotations calls imageStore.setShowAnnotations", () => {
    const store = makeImageStore({ showAnnotations: true });
    renderModal({ imageStore: store });

    fireEvent.click(screen.getByRole("checkbox"));
    expect(store.setShowAnnotations).toHaveBeenCalledWith(false);
  });

  test("clicking annotation rect sets selected annotation id", () => {
    const store = makeImageStore();
    renderModal({ imageStore: store });

    const rect = document.getElementById("annotation-rect-0");
    expect(rect).toBeTruthy();

    fireEvent.click(rect);
    expect(store.setSelectedAnnotationId).toHaveBeenCalledWith("ann-1");
  });

  test("match results button opens iaResults when selected annotation exists", () => {
    const store = makeImageStore({
      matchResultClickable: true,
      selectedAnnotationId: "ann-1",
    });

    renderModal({ imageStore: store });

    fireEvent.click(screen.getByText("MATCH_RESULTS"));

    expect(global.open).toHaveBeenCalledWith(
      "/react/match-results?taskId=task-123",
      "_blank",
    );
  });

  test("match results button is disabled when matchResultClickable is false", () => {
    const store = makeImageStore({
      matchResultClickable: false,
      selectedAnnotationId: "ann-1",
    });

    renderModal({ imageStore: store });

    const btn = screen.getByText("MATCH_RESULTS").closest("button");
    expect(btn).toBeDisabled();
  });

  test("NEW_MATCH button is disabled when hasMatchableAnnotations is false", () => {
    // encounterAnnotations has a positive-bbox annotation so the OLD isTrivial/bbox
    // gate would ENABLE the button — the button is disabled ONLY once the gate reads
    // hasMatchableAnnotations. This guarantees the test fails before the impl.
    const store = makeImageStore({
      hasMatchableAnnotations: false,
      encounterAnnotations: [
        { id: "ann-1", isTrivial: false, boundingBox: [0, 0, 10, 10] },
      ],
    });
    renderModal({ imageStore: store });

    const btn = screen.getByText("NEW_MATCH").closest("button");
    expect(btn).toBeDisabled();
  });

  test("delete image button confirms and calls imageStore.deleteImage", async () => {
    const store = makeImageStore();
    renderModal({ imageStore: store });

    fireEvent.click(screen.getByText("DELETE_IMAGE"));

    expect(window.confirm).toHaveBeenCalled();

    await waitFor(() => {
      expect(store.deleteImage).toHaveBeenCalledTimes(1);
    });
  });

  test("returns null when assets is empty", () => {
    const { container } = render(
      <ImageModal
        onClose={jest.fn()}
        assets={[]}
        index={0}
        setIndex={jest.fn()}
        rects={[]}
        imageStore={makeImageStore()}
      />,
    );

    expect(container.firstChild).toBeNull();
  });
});

describe("ImageModal annotation icon placement (#1534)", () => {
  // jsdom has no layout: stub the measurements the component reads on image load.
  const setDims = (el, width, height) => {
    Object.defineProperty(el, "clientWidth", {
      value: width,
      configurable: true,
    });
    Object.defineProperty(el, "clientHeight", {
      value: height,
      configurable: true,
    });
  };

  // 800x600 source shown in a 400x300 box -> every source px is half a display px.
  const loadImage = ({ measureBox = true } = {}) => {
    const img = screen.getByAltText("asset-ma-1");
    const box = document.getElementById("image-modal-image-box");
    setDims(img, 400, 300);
    if (measureBox) setDims(box, 400, 300);
    fireEvent.load(img);
    const rect = document.getElementById("annotation-rect-0");
    expect(rect).toBeTruthy();
    return rect;
  };

  const clusterOf = (rect) => rect.querySelector(".d-flex.flex-column");
  const rectAt = (overrides) => [{ ...rects[0], ...overrides }];

  test("a box inside the image keeps edit/delete at the top-right corner", () => {
    renderModal({ rects: rectAt({ x: 10, y: 20, width: 200, height: 100 }) });
    const cluster = clusterOf(loadImage());

    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.right).toBe("0px");
    expect(cluster.style.left).toBe("");
  });

  test("a box running off the right edge moves edit/delete to the top-left corner", () => {
    // displayed x 350..450 in a 400px-wide box
    renderModal({ rects: rectAt({ x: 700, y: 20, width: 200, height: 100 }) });
    const cluster = clusterOf(loadImage());

    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.left).toBe("0px");
    expect(cluster.style.right).toBe("");
  });

  test("a rotated box is judged after rotation: a quarter-turned box poking out the top keeps top-right", () => {
    // displayed x 100, y -10, w 200, h 60; after a 90° turn its local top-right
    // corner sits well inside the image, so the icons must not move.
    renderModal({
      rects: rectAt({
        x: 200,
        y: -20,
        width: 400,
        height: 120,
        rotation: Math.PI / 2,
      }),
    });
    const cluster = clusterOf(loadImage());

    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.right).toBe("0px");
    expect(cluster.style.left).toBe("");
  });

  test("zooming does not change where the icons are anchored", () => {
    renderModal({ rects: rectAt({ x: 700, y: 20, width: 200, height: 100 }) });
    loadImage();

    fireEvent.click(screen.getByTitle("Zoom In"));

    const cluster = clusterOf(document.getElementById("annotation-rect-0"));
    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.left).toBe("0px");
    expect(cluster.style.right).toBe("");
  });

  test("an overflowing box keeps the default corner until the image box is measured", () => {
    renderModal({ rects: rectAt({ x: 700, y: 20, width: 200, height: 100 }) });
    const cluster = clusterOf(loadImage({ measureBox: false }));

    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.right).toBe("0px");
    expect(cluster.style.left).toBe("");
  });

  test("a box wider than the image gets edit/delete slid into the visible strip", () => {
    // displayed x -50..450 in a 400px-wide box: no corner is visible, so the
    // cluster is anchored flush with the image's right edge instead.
    renderModal({
      rects: rectAt({ x: -100, y: 20, width: 1000, height: 200 }),
    });
    const cluster = clusterOf(loadImage());

    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.left).toBe("428px");
    expect(cluster.style.right).toBe("");
  });
});
