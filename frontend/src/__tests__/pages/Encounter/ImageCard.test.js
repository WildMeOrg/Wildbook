/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";

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
    delete window.open;
    window.open = jest.fn();
  });

  test("renders header, current filename, tags count, and main image", () => {
    const store = makeStore();
    renderCard(store);

    expect(screen.getByText("IMAGES")).toBeInTheDocument();
    expect(screen.getByText("first.jpg")).toBeInTheDocument();
    expect(screen.getByText("2 tags")).toBeInTheDocument();
    const img = screen.getByAltText("encounter image");
    expect(img).toHaveAttribute("src", "http://img/1.jpg");
    expect(store.setIntl).toHaveBeenCalled();
  });

  test("shows thumbnails and clicking one switches image via store", async () => {
    const store = makeStore();
    renderCard(store);

    const thumbs = screen.getAllByAltText(/media-/);
    expect(thumbs).toHaveLength(2);

    await userEvent.click(thumbs[1]);
    expect(store.setSelectedImageIndex).toHaveBeenCalledWith(1);
  });

  test("clicking image opens ImageModal and can be closed", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    const imgBox = screen.getByAltText("encounter image").parentElement;
    await user.click(imgBox);

    expect(screen.getByTestId("image-modal")).toBeInTheDocument();

    await user.click(screen.getByText("close"));
    expect(screen.queryByTestId("image-modal")).not.toBeInTheDocument();
  });

  test("clicking NEW_MATCH calls store.modals.setOpenMatchCriteriaModal when image exists", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    await user.click(screen.getByText("NEW_MATCH"));
    expect(store.modals.setOpenMatchCriteriaModal).toHaveBeenCalledWith(true);
  });

  test("clicking VISUAL_MATCHER opens visual matcher page when image exists", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    await user.click(screen.getByText("VISUAL_MATCHER"));
    expect(window.open).toHaveBeenCalledTimes(1);
    const url = window.open.mock.calls[0][0];
    expect(url).toContain("/encounters/encounterVM.jsp?number=E-1");
  });

  test("clicking ADD_ANNOTATION opens manual annotation page", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    renderCard(store);

    await user.click(screen.getByText("ADD_ANNOTATION"));
    expect(window.open).toHaveBeenCalledTimes(1);
    const url = window.open.mock.calls[0][0];
    expect(url).toContain(
      "/react/manual-annotation?encounterId=E-1&assetId=A1",
    );
  });

  test("renders upload slot and clicking container triggers file input click", () => {
    const store = makeStore({
      flow: { assignBrowse: jest.fn() },
    });
    renderCard(store);

    const uploader = screen.getByText("ADD_IMAGE").closest("#add-more-files");
    const input =
      screen.getByLabelText("ADD_IMAGE", { selector: "input" }) ||
      document.getElementById("add-more-files-input");
    const spy = jest.spyOn(input, "click");

    fireEvent.click(uploader);
    expect(spy).toHaveBeenCalled();
  });

  test("when isUploading true shows spinner and progress", () => {
    const store = makeStore({
      isUploading: true,
      uploadProgress: 45,
    });
    renderCard(store);

    expect(screen.getByText("45%")).toBeInTheDocument();
  });

  test("if no mediaAssets, rects should be empty and image src blank", () => {
    const store = makeStore({
      encounterData: { id: "E-1", mediaAssets: [] },
    });
    renderCard(store);

    const img = screen.getByAltText("encounter image");
    expect(img).toHaveAttribute("src", "");
  });

  test("calls initializeFlow when store.flow is null", () => {
    const store = makeStore({ flow: null, initializeFlow: jest.fn() });
    renderCard(store);

    expect(store.initializeFlow).toHaveBeenCalledTimes(1);
    expect(store.initializeFlow.mock.calls[0][1]).toBe(10);
  });

  test("when matchResultClickable=true it opens iaResults directly", async () => {
    const user = userEvent.setup();
    const store = makeStore({
      matchResultClickable: true,
      encounterData: {
        ...baseEncounterData,
      },
      imageModal: {
        selectedAnnotationId: "ann-1",
      },
    });

    store.encounterAnnotations = [
      {
        id: "ann-1",
        iaTaskId: "TASK-99",
        boundingBox: [10, 20, 100, 40],
      },
    ];

    renderCard(store);

    await user.click(screen.getByText("MATCH_RESULTS"));
    expect(window.open).toHaveBeenCalledTimes(1);
    const url = window.open.mock.calls[0][0];
    expect(url).toContain("/react/match-results?taskId=TASK-99");
  });

  test("clicking MATCH_RESULTS on foreign encounter -> fetches encounter and may open", async () => {
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
    });

    const { getByText } = renderCard(store);

    const axios = require("axios");
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

    const rectDiv = document.querySelector('[id^="rect-"]');
    await user.click(rectDiv);

    await user.click(getByText("MATCH_RESULTS"));

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalledWith("/api/v3/encounters/E-2");
    });

    await waitFor(() => {
      expect(window.open).toHaveBeenCalledTimes(1);
    });
    const url = window.open.mock.calls[0][0];
    expect(url).toContain("/react/match-results?taskId=TASK-FR-1");
  });

  test("rects are cleared when encounter has no mediaAssets", () => {
    const store = makeStore({
      encounterData: { id: "E-1", mediaAssets: [] },
    });
    renderCard(store);

    const rect = document.querySelector('[id^="rect-"]');
    expect(rect).toBeNull();
  });
});
