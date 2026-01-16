/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";
import "@testing-library/jest-dom";

jest.mock("swiper/css", () => ({}), { virtual: true });
jest.mock("swiper/css/navigation", () => ({}), { virtual: true });
jest.mock("swiper/css/thumbs", () => ({}), { virtual: true });
jest.mock("swiper/css/zoom", () => ({}), { virtual: true });

jest.mock("react-intl", () => ({
  FormattedMessage: ({ defaultMessage, id }) => (
    <span>{defaultMessage ?? id}</span>
  ),
  useIntl: () => ({
    formatMessage: ({ defaultMessage, id }) => defaultMessage ?? id,
  }),
}));

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/MainButton", () => {
  return ({ children, onClick, disabled, style }) => (
    <button
      style={style}
      disabled={disabled}
      onClick={onClick}
      data-testid="main-btn"
    >
      {children}
    </button>
  );
});

jest.mock("../../../components/FullScreenLoader", () => () => (
  <div data-testid="loader">Loading</div>
));

const modalSpy = jest.fn();
jest.mock(
  "../../../pages/SearchPages/searchResultTabs/ImageGalleryModal",
  () => (props) => {
    const modalSpy = jest.fn();
    modalSpy(props);
    return props.open ? <div data-testid="modal-open" /> : null;
  },
);

const ThemeColorContext = React.createContext({
  primaryColors: { primary500: "#123456" },
});
jest.mock("../../../ThemeColorProvider", () => {
  const React = require("react");
  const ThemeColorContext = React.createContext({
    primaryColors: { primary500: "#123456" },
  });
  return ThemeColorContext;
});

import GalleryView from "../../../pages/SearchPages/searchResultTabs/GalleryView";

function makeStore(overrides = {}) {
  const store = {
    loadingAll: false,
    currentPage: 0,
    pageSize: 2,
    previousPageItems: {},

    currentPageItems: [],
    setCurrentPageItems: jest.fn(function (items) {
      this.currentPageItems = items;
    }),

    setCurrentPage: jest.fn(function (n) {
      this.currentPage = n;
    }),

    setPreviousPageItems: jest.fn(function (page, items) {
      this.previousPageItems[page] = items;
    }),

    resetGallery: jest.fn(),

    imageModalStore: {
      selectedImageIndex: 0,
      setSelectedImageIndex: jest.fn(function (i) {
        this.selectedImageIndex = i;
      }),
      selectedAnnotationId: null,
      setSelectedAnnotationId: jest.fn(function (id) {
        this.selectedAnnotationId = id;
      }),
    },

    ...overrides,
  };

  return store;
}

function renderWithProviders(ui) {
  return render(
    <ThemeColorContext.Provider
      value={{ primaryColors: { primary500: "#123456" } }}
    >
      {ui}
    </ThemeColorContext.Provider>,
  );
}

function loadImage(imgEl, { naturalWidth = 1000, naturalHeight = 500 } = {}) {
  Object.defineProperty(imgEl, "naturalWidth", {
    value: naturalWidth,
    configurable: true,
  });
  Object.defineProperty(imgEl, "naturalHeight", {
    value: naturalHeight,
    configurable: true,
  });
  Object.defineProperty(imgEl, "clientWidth", {
    value: 300,
    configurable: true,
  });
  Object.defineProperty(imgEl, "clientHeight", {
    value: 200,
    configurable: true,
  });
  fireEvent.load(imgEl);
}

describe("GalleryView", () => {
  beforeEach(() => {
    modalSpy.mockClear();
  });

  test("calls resetGallery on mount and unmount, and calls pg once on mount", () => {
    const store = makeStore();
    const pg = jest.fn();

    const { unmount } = renderWithProviders(
      <GalleryView store={store} pg={pg} />,
    );

    expect(store.resetGallery).toHaveBeenCalledTimes(1);
    expect(pg).toHaveBeenCalledTimes(1);

    unmount();
    expect(store.resetGallery).toHaveBeenCalledTimes(2);
  });

  test("shows loader when loadingAll is true", () => {
    const store = makeStore({ loadingAll: true });
    renderWithProviders(<GalleryView store={store} pg={jest.fn()} />);

    expect(screen.getByTestId("loader")).toBeInTheDocument();
  });

  test('renders "No images" when currentPageItems is empty', () => {
    const store = makeStore({ currentPageItems: [] });
    renderWithProviders(<GalleryView store={store} pg={jest.fn()} />);

    expect(screen.getByText(/No images/i)).toBeInTheDocument();
  });

  test("renders images and opens modal on image click, passing correct index", () => {
    const assets = [
      { __k: "a1", id: "1", url: "http://x/1.jpg", annotations: [] },
      { __k: "a2", id: "2", url: "http://x/2.jpg", annotations: [] },
    ];
    const store = makeStore({ currentPageItems: assets, pageSize: 2 });

    renderWithProviders(<GalleryView store={store} pg={jest.fn()} />);

    const imgs = screen.getAllByRole("img");
    expect(imgs).toHaveLength(2);

    fireEvent.click(imgs[1]);

    expect(store.imageModalStore.setSelectedImageIndex).toHaveBeenCalledWith(1);
    expect(screen.getByTestId("modal-open")).toBeInTheDocument();
  });

  test("previous button is disabled on first page; next disabled if fewer than pageSize items", () => {
    const store = makeStore({
      currentPage: 0,
      pageSize: 3,
      currentPageItems: [{ __k: "a1", url: "#", annotations: [] }],
    });

    renderWithProviders(<GalleryView store={store} pg={jest.fn()} />);

    const buttons = screen.getAllByTestId("main-btn");
    const prev = buttons[0];
    const next = buttons[1];

    expect(prev).toBeDisabled();
    expect(next).toBeDisabled();
  });

  test("clicking next caches current page and calls pg when next page not cached", async () => {
    const store = makeStore({
      currentPage: 0,
      pageSize: 2,
      currentPageItems: [
        { __k: "a1", url: "#", annotations: [] },
        { __k: "a2", url: "#", annotations: [] },
      ],
    });
    const pg = jest.fn();

    renderWithProviders(<GalleryView store={store} pg={pg} />);
    pg.mockClear();
    const next = screen.getAllByTestId("main-btn")[1];
    await act(async () => {
      fireEvent.click(next);
    });

    expect(store.setPreviousPageItems).toHaveBeenCalledWith(
      0,
      store.currentPageItems.slice(),
    );

    expect(pg).toHaveBeenCalledTimes(1);

    expect(store.setCurrentPage).toHaveBeenCalledWith(1);
  });

  test("clicking previous uses cached items when available", () => {
    const store = makeStore({
      currentPage: 1,
      pageSize: 2,
      currentPageItems: [
        { __k: "b1", url: "#", annotations: [] },
        { __k: "b2", url: "#", annotations: [] },
      ],
      previousPageItems: {
        0: [
          { __k: "a1", url: "#", annotations: [] },
          { __k: "a2", url: "#", annotations: [] },
        ],
      },
    });

    renderWithProviders(<GalleryView store={store} pg={jest.fn()} />);

    const prev = screen.getAllByTestId("main-btn")[0];
    fireEvent.click(prev);

    expect(store.setCurrentPageItems).toHaveBeenCalledWith(
      store.previousPageItems[0].slice(),
    );
    expect(store.setCurrentPage).toHaveBeenCalledWith(0);
  });

  test("renders annotation rectangles and selects annotation on click", () => {
    const asset = {
      __k: "a1",
      id: "1",
      url: "http://x/1.jpg",
      annotations: [
        {
          id: "ann1",
          isTrivial: false,
          boundingBox: [10, 20, 100, 50],
          theta: 0.1,
          encounterId: "enc1",
          viewpoint: "L",
          iaClass: "whale",
        },
        { id: "trivial", isTrivial: true, boundingBox: [0, 0, 10, 10] },
        { id: "noBox", isTrivial: false },
      ],
    };

    const store = makeStore({
      currentPageItems: [asset],
      pageSize: 1,
    });

    renderWithProviders(<GalleryView store={store} pg={jest.fn()} />);

    const img = screen.getByRole("img");
    loadImage(img, { naturalWidth: 1000, naturalHeight: 500 });

    const rect = screen
      .getByRole("img")
      .parentElement.querySelector(
        "div[style*='position: absolute'], .position-absolute",
      );
    expect(rect).toBeInTheDocument();

    fireEvent.click(rect);
    expect(store.imageModalStore.setSelectedAnnotationId).toHaveBeenCalledWith(
      "ann1",
    );
  });

  test("resets selectedImageIndex to 0 when page items change", () => {
    const store = makeStore({
      currentPageItems: [],
    });

    const { rerender } = renderWithProviders(
      <GalleryView store={store} pg={jest.fn()} />,
    );

    store.currentPageItems = [{ __k: "x1", url: "#", annotations: [] }];
    rerender(
      <ThemeColorContext.Provider
        value={{ primaryColors: { primary500: "#123456" } }}
      >
        <GalleryView store={store} pg={jest.fn()} />
      </ThemeColorContext.Provider>,
    );

    expect(store.imageModalStore.setSelectedImageIndex).toHaveBeenCalledWith(0);
  });
});
