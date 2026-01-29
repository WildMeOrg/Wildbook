/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";

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
            onSwiper({ slideTo: () => {}, destroyed: false });
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
      wildMeColors: {
        cyan700: "#00abc2",
      },
      statusColors: {
        red500: "#ff0000",
      },
    }),
  };
});

import ImageModal from "../../../components/ImageModal";

const makeImageStore = (overrides = {}) => ({
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
  matchResultClickable: true,
  encounterAnnotations: [
    {
      id: "ann-1",
      iaTaskId: "task-123",
    },
  ],
  deleteImage: jest.fn(async () => {}),
  removeAnnotation: jest.fn(async () => {}),
  refreshEncounterData: jest.fn(),
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

describe("ImageModal", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.open = jest.fn();
  });

  test("renders modal with image and sidebar", () => {
    const store = makeImageStore();
    render(
      <ImageModal
        onClose={jest.fn()}
        assets={assets}
        index={0}
        setIndex={jest.fn()}
        rects={rects}
        imageStore={store}
      />,
    );

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByAltText("asset-ma-1")).toBeInTheDocument();
    expect(screen.getAllByTestId("swiper-slide").length).toBe(2);
    expect(screen.getByText("DELETE_IMAGE")).toBeInTheDocument();
  });

  test("click close button calls onClose", () => {
    const onClose = jest.fn();
    const store = makeImageStore();
    render(
      <ImageModal
        onClose={onClose}
        assets={assets}
        index={0}
        setIndex={jest.fn()}
        rects={rects}
        imageStore={store}
      />,
    );

    const closeBtn = screen.getByLabelText("Close");
    fireEvent.click(closeBtn);
    expect(onClose).toHaveBeenCalled();
  });

  test("next/prev buttons change index", () => {
    const setIndex = jest.fn();
    const store = makeImageStore();
    render(
      <ImageModal
        onClose={jest.fn()}
        assets={assets}
        index={0}
        setIndex={setIndex}
        rects={rects}
        imageStore={store}
      />,
    );

    const nextBtn = screen.getByLabelText("Next image");
    fireEvent.click(nextBtn);
    expect(setIndex).toHaveBeenCalledWith(1);
  });

  test("toggle show annotations calls imageStore.setShowAnnotations", () => {
    const store = makeImageStore();
    render(
      <ImageModal
        onClose={jest.fn()}
        assets={assets}
        index={0}
        setIndex={jest.fn()}
        rects={rects}
        imageStore={store}
      />,
    );

    const checkbox = screen.getByRole("checkbox");
    fireEvent.click(checkbox);
    expect(store.setShowAnnotations).toHaveBeenCalledWith(false);
  });

  test("click annotation sets selectedAnnotationId", () => {
    const store = makeImageStore();
    render(
      <ImageModal
        onClose={jest.fn()}
        assets={assets}
        index={0}
        setIndex={jest.fn()}
        rects={rects}
        imageStore={store}
      />,
    );

    const absDiv = document.querySelector(
      "#image-modal-image .position-absolute",
    );
    fireEvent.click(absDiv);
  });

  test("match results button opens iaResults when clickable and selected", () => {
    const store = makeImageStore({
      matchResultClickable: true,
      selectedAnnotationId: "ann-1",
    });
    render(
      <ImageModal
        onClose={jest.fn()}
        assets={assets}
        index={0}
        setIndex={jest.fn()}
        rects={rects}
        imageStore={store}
      />,
    );

    const matchBtn = screen.getByText("MATCH_RESULTS");
    fireEvent.click(matchBtn);

    expect(global.open).toHaveBeenCalledWith(
      "/react/match-results?taskId=task-123",
      "_blank",
    );
  });

  test("delete image button confirms and calls imageStore.deleteImage", () => {
    const store = makeImageStore();
    const confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(true);

    render(
      <ImageModal
        onClose={jest.fn()}
        assets={assets}
        index={0}
        setIndex={jest.fn()}
        rects={rects}
        imageStore={store}
      />,
    );

    const delBtn = screen.getByText("DELETE_IMAGE");
    fireEvent.click(delBtn);

    expect(confirmSpy).toHaveBeenCalled();
    expect(store.deleteImage).toHaveBeenCalled();

    confirmSpy.mockRestore();
  });
});
