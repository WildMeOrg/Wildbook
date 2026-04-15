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

    expect(window.open).toHaveBeenCalledWith(
      "/iaResults.jsp?taskId=task-123",
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
