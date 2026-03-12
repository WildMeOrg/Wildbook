/* eslint-disable react/display-name */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
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
    expect(window.open.mock.calls[0][0]).toContain(
      "/iaResults.jsp?taskId=TASK-99",
    );
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

    expect(window.open.mock.calls[0][0]).toContain(
      "/iaResults.jsp?taskId=TASK-FR-1",
    );
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
});
