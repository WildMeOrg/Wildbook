/* eslint-disable react/display-name */
import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";
import axios from "axios";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("axios", () => ({
  get: jest.fn(),
}));

jest.mock("../../../components/ImageModal", () => {
  const Mock = (props) => (
    <div data-testid="image-modal">
      modal
      <button onClick={props.onClose}>close</button>
    </div>
  );
  Mock.displayName = "MockImageModal";
  return Mock;
});

jest.mock(
  "../../../components/ToolTip",
  () => (props) =>
    props.show ? (
      <div data-testid="tooltip" style={{ left: props.x, top: props.y }}>
        {props.children}
      </div>
    ) : null,
);

jest.mock("../../../components/icons/MailIcon", () => () => (
  <span data-testid="icon-mail" />
));
jest.mock("../../../components/icons/ImageIcon", () => () => (
  <span data-testid="icon-image" />
));
jest.mock("../../../components/icons/FullscreenIcon", () => () => (
  <span data-testid="icon-fullscreen" />
));
jest.mock("../../../components/icons/MatchResultIcon", () => () => (
  <span data-testid="icon-match" />
));
jest.mock("../../../components/icons/RefreshIcon", () => () => (
  <span data-testid="icon-refresh" />
));
jest.mock("../../../components/icons/PencilIcon", () => () => (
  <span data-testid="icon-pencil" />
));
jest.mock("../../../components/icons/EyeIcon", () => () => (
  <span data-testid="icon-eye" />
));

import ThemeColorContext from "../../../ThemeColorProvider";
import ImageCard from "../../../pages/Encounter/ImageCard";

const ThemeWrapper = ({ children }) => (
  <ThemeColorContext.Provider
    value={{
      primaryColors: {
        primary50: "#eee",
        primary500: "#123",
      },
    }}
  >
    {children}
  </ThemeColorContext.Provider>
);

const baseEncounterData = {
  id: "E-1",
  mediaAssets: [
    {
      id: "A1",
      url: "http://img/1.jpg",
      width: 1000,
      height: 500,
      userFilename: "first.jpg",
      keywords: ["k1", "k2"],
      annotations: [
        {
          id: "ann-1",
          encounterId: "E-1",
          boundingBox: [10, 20, 100, 40],
          theta: 0,
          viewpoint: "left",
          iaClass: "whale",
        },
      ],
    },
    {
      id: "A2",
      url: "http://img/2.jpg",
      width: 1200,
      height: 600,
      userFilename: "second.jpg",
      keywords: [],
      annotations: [],
    },
  ],
};

const makeStore = (overrides = {}) => ({
  access: "write",
  encounterData: baseEncounterData,
  encounterAnnotations: baseEncounterData.mediaAssets[0].annotations,
  selectedImageIndex: 0,
  setSelectedImageIndex: jest.fn(),
  setSelectedAnnotationId: jest.fn(),
  setIntl: jest.fn(),
  matchResultClickable: false,
  hasMatchableAnnotations: true,
  modals: {
    setOpenMatchCriteriaModal: jest.fn(),
  },
  imageModal: {
    selectedAnnotationId: null,
    encounterData: baseEncounterData,
    selectedImageIndex: 0,
    removeAnnotation: jest.fn(),
    setSelectedAnnotationId: jest.fn(),
    refreshEncounterData: jest.fn(),
  },
  flow: null,
  initializeFlow: jest.fn(),
  isUploading: false,
  uploadProgress: 0,
  siteSettingsData: {},
  ...overrides,
});

const renderCard = (store) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <ThemeWrapper>
        <ImageCard store={store} />
      </ThemeWrapper>
    </IntlProvider>,
  );

describe("ImageCard", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    window.open = jest.fn();
    window.alert = jest.fn();
    window.confirm = jest.fn(() => true);
  });

  test("renders header, filename, keyword count, and main image", () => {
    const store = makeStore();
    renderCard(store);

    expect(screen.getByText("IMAGES")).toBeInTheDocument();
    expect(screen.getByText("first.jpg")).toBeInTheDocument();
    expect(screen.getByText("2 Keywords")).toBeInTheDocument();

    const img = screen.getByAltText("encounter image");
    expect(img).toHaveAttribute("src", "http://img/1.jpg");

    expect(store.setIntl).toHaveBeenCalled();
  });

  test("renders thumbnails and clicking thumbnail switches image", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    const thumbs = screen.getAllByAltText(/media-/);
    expect(thumbs).toHaveLength(2);

    await user.click(thumbs[1]);
    expect(store.setSelectedImageIndex).toHaveBeenCalledWith(1);
  });

  test("clicking image area opens ImageModal and close button closes it", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    const imageBox = screen.getByAltText("encounter image").parentElement;
    await user.click(imageBox);

    expect(screen.getByTestId("image-modal")).toBeInTheDocument();

    await user.click(screen.getByText("close"));
    expect(screen.queryByTestId("image-modal")).not.toBeInTheDocument();
  });

  test("clicking NEW_MATCH opens match criteria modal", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    await user.click(screen.getByText("NEW_MATCH"));
    expect(store.modals.setOpenMatchCriteriaModal).toHaveBeenCalledWith(true);
  });

  test("NEW_MATCH does nothing when hasMatchableAnnotations is false", async () => {
    const user = userEvent.setup();
    const store = makeStore({ hasMatchableAnnotations: false });
    renderCard(store);

    await user.click(screen.getByText("NEW_MATCH"));
    expect(store.modals.setOpenMatchCriteriaModal).not.toHaveBeenCalled();
  });

  test("clicking VISUAL_MATCHER opens visual matcher page", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    await user.click(screen.getByText("VISUAL_MATCHER"));

    expect(window.open).toHaveBeenCalledTimes(1);
    expect(window.open.mock.calls[0][0]).toContain(
      "/encounters/encounterVM.jsp?number=E-1",
    );
  });

  test("clicking ADD_ANNOTATION opens manual annotation page", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    await user.click(screen.getByText("ADD_ANNOTATION"));

    expect(window.open).toHaveBeenCalledTimes(1);
    expect(window.open.mock.calls[0][0]).toContain(
      "/react/manual-annotation?encounterId=E-1&assetId=A1",
    );
  });

  test("shows upload progress when uploading", () => {
    const store = makeStore({
      isUploading: true,
      uploadProgress: 45,
      flow: { assignBrowse: jest.fn() },
    });

    renderCard(store);
    expect(screen.getByText("45%")).toBeInTheDocument();
  });

  test("shows no-image message when mediaAssets is empty", () => {
    const store = makeStore({
      encounterData: { id: "E-1", mediaAssets: [] },
      encounterAnnotations: [],
    });

    renderCard(store);

    expect(screen.queryByAltText("encounter image")).not.toBeInTheDocument();
    expect(screen.getByText("NO_IMAGE_AVAILABLE")).toBeInTheDocument();

    const rect = document.querySelector('[id^="rect-"]');
    expect(rect).toBeNull();
  });

  test("calls initializeFlow when store.flow is null (default maxSize=3)", () => {
    const store = makeStore({ flow: null, initializeFlow: jest.fn() });

    renderCard(store);

    expect(store.initializeFlow).toHaveBeenCalledTimes(1);
    expect(store.initializeFlow.mock.calls[0][1]).toBe(3);
  });

  test("calls assignBrowse when flow already exists", () => {
    const assignBrowse = jest.fn();
    const store = makeStore({
      flow: { assignBrowse },
    });

    renderCard(store);

    expect(assignBrowse).toHaveBeenCalledTimes(1);
    const input = document.getElementById("add-more-files-input");
    expect(assignBrowse).toHaveBeenCalledWith(input);
  });

  test("MATCH_RESULTS opens iaResults directly when matchResultClickable=true", async () => {
    const user = userEvent.setup();
    const imageModal = {
      selectedAnnotationId: "ann-1",
      encounterData: baseEncounterData,
      selectedImageIndex: 0,
      removeAnnotation: jest.fn(),
      setSelectedAnnotationId: jest.fn(),
      refreshEncounterData: jest.fn(),
    };

    const store = makeStore({
      matchResultClickable: true,
      imageModal,
      encounterAnnotations: [
        {
          id: "ann-1",
          iaTaskId: "TASK-99",
          boundingBox: [10, 20, 100, 40],
        },
      ],
    });

    renderCard(store);

    await user.click(screen.getByText("MATCH_RESULTS"));

    expect(window.open).toHaveBeenCalledTimes(1);
    const url = window.open.mock.calls[0][0];
    expect(url).toContain("/react/match-results?taskId=TASK-99");
  });

  test("MATCH_RESULTS for foreign annotation fetches encounter and opens iaResults if available", async () => {
    const user = userEvent.setup();

    const store = makeStore({
      encounterData: {
        id: "E-1",
        mediaAssets: [
          {
            id: "A1",
            url: "http://img/1.jpg",
            width: 1000,
            height: 500,
            userFilename: "first.jpg",
            keywords: [],
            annotations: [
              {
                id: "ann-foreign",
                encounterId: "E-2",
                boundingBox: [10, 20, 100, 40],
                theta: 0,
                viewpoint: "left",
                iaClass: "whale",
              },
            ],
          },
        ],
      },
      encounterAnnotations: [],
    });

    axios.get.mockResolvedValueOnce({
      data: {
        id: "E-2",
        mediaAssets: [
          {
            id: "AX",
            detectionStatus: "complete",
            annotations: [
              {
                id: "ann-foreign",
                iaTaskId: "TASK-FR-1",
                identificationStatus: "complete",
                iaTaskParameters: {},
              },
            ],
          },
        ],
      },
    });

    renderCard(store);

    const rectDiv = document.querySelector('[id^="rect-"]');
    expect(rectDiv).toBeTruthy();

    await user.click(rectDiv); // select foreign annotation
    await user.click(screen.getByText("MATCH_RESULTS"));

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalledWith("/api/v3/encounters/E-2");
    });

    await waitFor(() => {
      expect(window.open).toHaveBeenCalledTimes(1);
    });
    const url = window.open.mock.calls[0][0];
    expect(url).toContain("/react/match-results?taskId=TASK-FR-1");
  });

  test("clicking MATCH_RESULTS without annotation shows alert", async () => {
    const user = userEvent.setup();
    const store = makeStore({
      matchResultClickable: false,
      imageModal: {
        selectedAnnotationId: null,
        encounterData: baseEncounterData,
        selectedImageIndex: 0,
        removeAnnotation: jest.fn(),
        setSelectedAnnotationId: jest.fn(),
        refreshEncounterData: jest.fn(),
      },
    });

    renderCard(store);

    await user.click(screen.getByText("MATCH_RESULTS"));
    expect(window.alert).toHaveBeenCalledWith(
      "Select an annotation to view match results.",
    );
  });

  describe("detection status banner", () => {
    const makeAsset = (detectionStatus) => ({
      id: "ma-det",
      url: "http://img/1.jpg",
      width: 1000,
      height: 500,
      userFilename: "photo.jpg",
      keywords: [],
      annotations: [],
      detectionStatus,
    });

    // helper that overrides encounterData with the given assets
    const renderWithAssets = (assets, extra = {}) =>
      renderCard(
        makeStore({
          encounterData: { id: "E-1", mediaAssets: assets },
          encounterAnnotations: [],
          ...extra,
        }),
      );

    test("no images: banner is not shown", () => {
      renderWithAssets([]);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    test("image with null detectionStatus: banner is shown (needs polling)", () => {
      renderWithAssets([makeAsset(null)]);
      expect(screen.getByRole("status")).toBeInTheDocument();
    });

    test("image with undefined detectionStatus: banner is shown (needs polling)", () => {
      renderWithAssets([makeAsset(undefined)]);
      expect(screen.getByRole("status")).toBeInTheDocument();
    });

    test("non-terminal detectionStatus 'running': banner is shown", () => {
      renderWithAssets([makeAsset("running")]);
      expect(screen.getByRole("status")).toBeInTheDocument();
    });

    test("detectionStatus 'complete': banner is not shown", () => {
      renderWithAssets([makeAsset("complete")]);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    test("detectionStatus 'error': banner is not shown", () => {
      renderWithAssets([makeAsset("error")]);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    test("detectionStatus 'pending': banner is not shown", () => {
      renderWithAssets([makeAsset("pending")]);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    test("banner reflects selectedImageIndex: index 0 complete hides banner even when index 1 is running", () => {
      renderWithAssets([makeAsset("complete"), makeAsset("running")], {
        selectedImageIndex: 0,
      });
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
  });
});

describe("ImageCard annotation icon placement (#1534)", () => {
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

  const annotationAt = (boundingBox, extra = {}) => ({
    id: "ann-1",
    encounterId: "E-1",
    boundingBox,
    theta: 0,
    viewpoint: "left",
    iaClass: "whale",
    ...extra,
  });

  // 1000x500 source shown in a 500x250 box -> every source px is half a display px.
  const renderWithAnnotation = (annotation, { measureBox = true } = {}) => {
    const asset = {
      ...baseEncounterData.mediaAssets[0],
      annotations: [annotation],
    };
    const encounterData = { ...baseEncounterData, mediaAssets: [asset] };
    const store = makeStore({
      encounterData,
      encounterAnnotations: [annotation],
    });
    store.imageModal.encounterData = encounterData;
    renderCard(store);

    const img = screen.getByAltText("encounter image");
    const clipBox = img.parentElement;
    setDims(img, 500, 250);
    if (measureBox) setDims(clipBox, 500, 250);
    fireEvent.load(img);
    return store;
  };

  const clickAnnotation = async (user) => {
    const rect = await waitFor(() => {
      const el = document.getElementById("rect-0");
      expect(el).toBeTruthy();
      return el;
    });
    await user.click(rect);
    return rect;
  };

  beforeEach(() => {
    window.open = jest.fn();
    window.confirm = jest.fn(() => true);
  });

  test("a box inside the image keeps edit/delete at the top-right corner", async () => {
    const user = userEvent.setup();
    renderWithAnnotation(annotationAt([10, 20, 100, 40]));
    const rect = await clickAnnotation(user);

    const cluster = rect.querySelector(".d-flex.flex-column");
    expect(cluster).toBeTruthy();
    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.right).toBe("0px");
    expect(cluster.style.left).toBe("");
  });

  test("a box running off the right edge moves edit/delete to the top-left corner", async () => {
    const user = userEvent.setup();
    // displayed x 400..550 in a 500px-wide box
    renderWithAnnotation(annotationAt([800, 20, 300, 40]));
    const rect = await clickAnnotation(user);

    const cluster = rect.querySelector(".d-flex.flex-column");
    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.left).toBe("0px");
    expect(cluster.style.right).toBe("");
  });

  test("a box running off the top edge moves edit/delete to the bottom-right corner", async () => {
    const user = userEvent.setup();
    // displayed y -30..50
    renderWithAnnotation(annotationAt([100, -60, 300, 160]));
    const rect = await clickAnnotation(user);

    const cluster = rect.querySelector(".d-flex.flex-column");
    expect(cluster.style.bottom).toBe("0px");
    expect(cluster.style.right).toBe("0px");
    expect(cluster.style.top).toBe("");
  });

  test("the go-to-encounter link of a foreign annotation relocates and keeps its inset", async () => {
    const user = userEvent.setup();
    renderWithAnnotation(
      annotationAt([800, 20, 300, 40], { encounterId: "E-2" }),
    );
    const rect = await clickAnnotation(user);

    const link = rect.querySelector(".d-flex");
    expect(link).toBeTruthy();
    expect(link.style.top).toBe("0px");
    expect(link.style.left).toBe("-2px");
    expect(link.style.right).toBe("");
  });

  test("an overflowing box keeps the default corner until the image box is measured", async () => {
    const user = userEvent.setup();
    renderWithAnnotation(annotationAt([800, 20, 300, 40]), {
      measureBox: false,
    });
    const rect = await clickAnnotation(user);

    const cluster = rect.querySelector(".d-flex.flex-column");
    expect(cluster.style.top).toBe("0px");
    expect(cluster.style.right).toBe("0px");
    expect(cluster.style.left).toBe("");
  });
});
