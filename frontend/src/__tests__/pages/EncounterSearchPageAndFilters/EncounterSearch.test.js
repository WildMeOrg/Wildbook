import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import EncounterSearch from "../../../pages/SearchPages/EncounterSearch";
import { MemoryRouter } from "react-router-dom";
import * as useFilterEncountersHook from "../../../models/encounters/useFilterEncounters";
import * as useFilterEncountersWithMediaAssetsHook from "../../../models/encounters/useFilterEncountersWithMediaAssets";
import * as useEncounterSearchSchemasHook from "../../../models/encounters/useEncounterSearchSchemas";
import * as getAllSearchParams from "../../../pages/SearchPages/getAllSearchParamsAndParse";
import { globalEncounterFormStore } from "../../../pages/SearchPages/stores/EncounterFormStore";
import axios from "axios";

jest.mock("../../../pages/SearchPages/stores/EncounterFormStore", () => ({
  globalEncounterFormStore: {
    formFilters: [],
    mediaAssetsSearchQuery: [],
    pageSize: 20,
    start: 0,
    assetOffset: 0,
    galleryLoading: false,
    loadingAll: false,
    galleryExhausted: false,
    setCurrentPageItems: jest.fn(),
    setAssetOffset: jest.fn(),
    setStart: jest.fn(),
    setSelectedRows: jest.fn(),
    setGalleryLoading: jest.fn(),
    setLoadingAll: jest.fn(),
    setGalleryExhausted: jest.fn(),
  },
}));

jest.mock("../../../components/DataTable", () => {
  const MockDataTable = (props) => (
    <div data-testid="datatable" style={props.style}>
      <div data-testid="datatable-total-items">{props.totalItems}</div>
      <div data-testid="datatable-page">{props.page}</div>
      <div data-testid="datatable-per-page">{props.perPage}</div>
      <button onClick={() => props.onRowClicked({ id: "enc1" })}>
        RowClick
      </button>
      <button onClick={() => props.onPageChange(1)}>PageChange</button>
      <button onClick={() => props.onPerPageChange(50)}>PerPageChange</button>
      <button
        onClick={() =>
          props.onSelectedRowsChange({
            selectedRows: [{ id: "selected-1" }],
          })
        }
      >
        SelectRows
      </button>
      <button
        onClick={() => props.setSort({ sortname: "date", sortorder: "asc" })}
      >
        SetSortAsc
      </button>
      <button onClick={() => props.pg()}>TriggerGalleryPagination</button>
    </div>
  );

  MockDataTable.displayName = "MockDataTable";

  return MockDataTable;
});

jest.mock("../../../components/FilterPanel", () => {
  const MockFilterPanel = (props) => (
    <div data-testid="filter-panel" style={props.style}>
      <button onClick={props.handleSearch}>Search</button>
    </div>
  );

  MockFilterPanel.displayName = "MockFilterPanel";

  return MockFilterPanel;
});

jest.mock("../../../components/filterFields/SideBar", () => {
  const MockSideBar = (props) => (
    <div data-testid="sidebar">
      <div data-testid="sidebar-query-id">{String(props.queryID)}</div>
    </div>
  );

  MockSideBar.displayName = "MockSideBar";

  return MockSideBar;
});

jest.mock("../../../pages/SearchPages/components/ExportModal", () => {
  const MockExportModal = ({ open }) => (
    <div data-testid="export-modal">{open ? "open" : "closed"}</div>
  );

  MockExportModal.displayName = "MockExportModal";

  return MockExportModal;
});

describe("EncounterSearch", () => {
  const mockFilterEncounters = {
    data: {
      results: [{ id: "enc1", date: "2023-01-01", access: "none" }],
      resultCount: 1,
      searchQueryId: "abc123",
    },
    loading: false,
    refetch: jest.fn(),
  };

  const mockSchemas = [{ id: "schema1", label: "Test Schema" }];

  beforeEach(() => {
    jest.clearAllMocks();

    globalEncounterFormStore.formFilters = [];
    globalEncounterFormStore.mediaAssetsSearchQuery = [];
    globalEncounterFormStore.pageSize = 20;
    globalEncounterFormStore.start = 0;
    globalEncounterFormStore.assetOffset = 0;
    globalEncounterFormStore.galleryLoading = false;
    globalEncounterFormStore.loadingAll = false;
    globalEncounterFormStore.galleryExhausted = false;

    jest
      .spyOn(useFilterEncountersHook, "default")
      .mockReturnValue(mockFilterEncounters);

    jest
      .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
      .mockReturnValue({ fetchMediaAssets: jest.fn() });

    jest
      .spyOn(useEncounterSearchSchemasHook, "default")
      .mockReturnValue(mockSchemas);

    jest
      .spyOn(getAllSearchParams, "helperFunction")
      .mockImplementation(() => {});

    jest.spyOn(window, "open").mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  const renderWithProviders = (url = "/search") =>
    render(
      <MemoryRouter initialEntries={[url]}>
        <EncounterSearch />
      </MemoryRouter>,
    );

  it("renders FilterPanel, DataTable, SideBar, and ExportModal", () => {
    renderWithProviders();

    expect(screen.getByTestId("filter-panel")).toBeInTheDocument();
    expect(screen.getByTestId("datatable")).toBeInTheDocument();
    expect(screen.getByTestId("sidebar")).toBeInTheDocument();
    expect(screen.getByTestId("export-modal")).toHaveTextContent("closed");
  });

  it("opens encounter details in a new tab when row is clicked", () => {
    renderWithProviders();

    fireEvent.click(screen.getByText("RowClick"));

    expect(window.open).toHaveBeenCalledWith(
      "/react/encounter?number=enc1",
      "_blank",
    );
  });

  it("calls store.setSelectedRows when rows are selected", () => {
    renderWithProviders();

    fireEvent.click(screen.getByText("SelectRows"));

    expect(globalEncounterFormStore.setSelectedRows).toHaveBeenCalledWith([
      { id: "selected-1" },
    ]);
  });

  it("updates search params when FilterPanel search button is clicked", async () => {
    renderWithProviders();

    fireEvent.click(screen.getByText("Search"));

    await waitFor(() => {
      expect(screen.getByTestId("filter-panel")).toBeInTheDocument();
    });
  });

  it("fetches search data with queryID via axios", async () => {
    jest.spyOn(axios, "get").mockResolvedValue({
      data: { hits: [{ id: "encX" }] },
      headers: { "x-wildbook-total-hits": "99" },
    });

    renderWithProviders("/search?searchQueryId=abc123");

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalledWith(
        expect.stringContaining("/api/v3/search/abc123"),
      );
    });
  });

  it("handles axios error gracefully", async () => {
    const consoleErrorSpy = jest
      .spyOn(console, "error")
      .mockImplementation(() => {});
    jest.spyOn(axios, "get").mockRejectedValue(new Error("fail"));

    renderWithProviders("/search?searchQueryId=abc123");

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalled();
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        "Error fetching search data:",
        expect.any(Error),
      );
    });
  });

  it("toggles panel state on popstate without crashing", () => {
    renderWithProviders();

    act(() => {
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    expect(screen.getByTestId("datatable")).toBeInTheDocument();
  });

  it("applies fixed background attachment style to the root container", () => {
    const { container } = renderWithProviders();

    expect(container.firstChild).toHaveStyle("background-attachment: fixed");
  });

  it("renders with empty encounter results", () => {
    jest.spyOn(useFilterEncountersHook, "default").mockReturnValue({
      data: { results: [], resultCount: 0, searchQueryId: "" },
      loading: false,
      refetch: jest.fn(),
    });

    renderWithProviders();

    expect(screen.getByTestId("datatable")).toBeInTheDocument();
    expect(screen.getByTestId("datatable-total-items")).toHaveTextContent("0");
  });

  describe("gallery pagination with access filtering", () => {
    it("filters out encounters with access 'none' from gallery results", async () => {
      const mockFetchMediaAssets = jest.fn().mockResolvedValue({
        data: {
          hits: [
            {
              id: "enc1",
              access: "none",
              mediaAssets: [{ id: "asset1", url: "http://x/1.jpg" }],
            },
            {
              id: "enc2",
              access: "read",
              individualId: "ind-2",
              individualDisplayName: "Ind 2",
              date: "2023-01-02",
              verbatimDate: "Jan 2, 2023",
              mediaAssets: [{ id: "asset2", url: "http://x/2.jpg" }],
            },
          ],
        },
      });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(globalEncounterFormStore.setCurrentPageItems).toHaveBeenCalled();
      });

      const items =
        globalEncounterFormStore.setCurrentPageItems.mock.calls[
          globalEncounterFormStore.setCurrentPageItems.mock.calls.length - 1
        ][0];

      expect(items[0]).toMatchObject({
        encounterId: "enc2",
        individualId: "ind-2",
        individualDisplayName: "Ind 2",
        date: "2023-01-02",
        verbatimDate: "Jan 2, 2023",
      });
    });

    it("fetches subsequent windows when first window contains only restricted encounters", async () => {
      const mockFetchMediaAssets = jest
        .fn()
        .mockResolvedValueOnce({
          data: {
            hits: Array(20)
              .fill(null)
              .map((_, i) => ({
                id: `restricted${i}`,
                access: "none",
                mediaAssets: [{ id: `asset${i}`, url: `http://x/${i}.jpg` }],
              })),
          },
        })
        .mockResolvedValueOnce({
          data: {
            hits: [
              {
                id: "enc1",
                access: "read",
                mediaAssets: [{ id: "asset1", url: "http://x/1.jpg" }],
              },
              {
                id: "enc2",
                access: "read",
                mediaAssets: [{ id: "asset2", url: "http://x/2.jpg" }],
              },
            ],
          },
        })
        .mockResolvedValueOnce({
          data: {
            hits: [],
          },
        });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(globalEncounterFormStore.setCurrentPageItems).toHaveBeenCalled();
      });

      expect(mockFetchMediaAssets).toHaveBeenNthCalledWith(
        1,
        expect.objectContaining({ from: 0, size: 20 }),
      );

      expect(mockFetchMediaAssets).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining({ from: 20, size: 20 }),
      );

      expect(mockFetchMediaAssets).toHaveBeenNthCalledWith(
        3,
        expect.objectContaining({ from: 22, size: 20 }),
      );

      const items =
        globalEncounterFormStore.setCurrentPageItems.mock.calls[
          globalEncounterFormStore.setCurrentPageItems.mock.calls.length - 1
        ][0];

      expect(items).toHaveLength(2);
      expect(items[0]).toMatchObject({ encounterId: "enc1" });
      expect(items[1]).toMatchObject({ encounterId: "enc2" });
    });

    it("returns empty when backend is exhausted and all encounters are restricted", async () => {
      const mockFetchMediaAssets = jest
        .fn()
        .mockResolvedValueOnce({
          data: {
            hits: [
              {
                id: "enc1",
                access: "none",
                mediaAssets: [{ id: "asset1", url: "http://x/1.jpg" }],
              },
              {
                id: "enc2",
                access: "none",
                mediaAssets: [{ id: "asset2", url: "http://x/2.jpg" }],
              },
            ],
          },
        })
        .mockResolvedValueOnce({
          data: {
            hits: [],
          },
        });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(
          globalEncounterFormStore.setCurrentPageItems,
        ).toHaveBeenCalledWith([]);
      });

      expect(mockFetchMediaAssets).toHaveBeenCalledTimes(2);
    });

    it("treats encounters with undefined access as accessible", async () => {
      const mockFetchMediaAssets = jest
        .fn()
        .mockResolvedValueOnce({
          data: {
            hits: [
              {
                id: "enc1",
                access: undefined,
                mediaAssets: [{ id: "asset1", url: "http://x/1.jpg" }],
              },
            ],
          },
        })
        .mockResolvedValueOnce({
          data: {
            hits: [],
          },
        });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(globalEncounterFormStore.setCurrentPageItems).toHaveBeenCalled();
      });

      const items =
        globalEncounterFormStore.setCurrentPageItems.mock.calls[
          globalEncounterFormStore.setCurrentPageItems.mock.calls.length - 1
        ][0];

      expect(items).toHaveLength(1);
      expect(items[0]).toMatchObject({ encounterId: "enc1" });
    });

    it("handles mixed access levels in pagination", async () => {
      const mockFetchMediaAssets = jest
        .fn()
        .mockResolvedValueOnce({
          data: {
            hits: [
              {
                id: "enc1",
                access: "none",
                mediaAssets: [{ id: "asset1", url: "http://x/1.jpg" }],
              },
              {
                id: "enc2",
                access: "read",
                mediaAssets: [{ id: "asset2", url: "http://x/2.jpg" }],
              },
              {
                id: "enc3",
                access: "none",
                mediaAssets: [{ id: "asset3", url: "http://x/3.jpg" }],
              },
              {
                id: "enc4",
                access: "write",
                mediaAssets: [{ id: "asset4", url: "http://x/4.jpg" }],
              },
            ],
          },
        })
        .mockResolvedValueOnce({
          data: {
            hits: [],
          },
        });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(globalEncounterFormStore.setCurrentPageItems).toHaveBeenCalled();
      });

      const items =
        globalEncounterFormStore.setCurrentPageItems.mock.calls[
          globalEncounterFormStore.setCurrentPageItems.mock.calls.length - 1
        ][0];

      expect(items).toHaveLength(2);
      expect(items[0]).toMatchObject({ encounterId: "enc2" });
      expect(items[1]).toMatchObject({ encounterId: "enc4" });
    });

    it("skips encounters with empty media assets array", async () => {
      const mockFetchMediaAssets = jest
        .fn()
        .mockResolvedValueOnce({
          data: {
            hits: [
              {
                id: "enc1",
                access: "read",
                mediaAssets: [],
              },
              {
                id: "enc2",
                access: "read",
                mediaAssets: [{ id: "asset2", url: "http://x/2.jpg" }],
              },
            ],
          },
        })
        .mockResolvedValueOnce({
          data: {
            hits: [],
          },
        });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(globalEncounterFormStore.setCurrentPageItems).toHaveBeenCalled();
      });

      const items =
        globalEncounterFormStore.setCurrentPageItems.mock.calls[
          globalEncounterFormStore.setCurrentPageItems.mock.calls.length - 1
        ][0];

      expect(items).toHaveLength(1);
      expect(items[0]).toMatchObject({ encounterId: "enc2" });
    });

    it("returns empty immediately when backend returns empty hits", async () => {
      const mockFetchMediaAssets = jest.fn().mockResolvedValue({
        data: {
          hits: [],
        },
      });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(
          globalEncounterFormStore.setCurrentPageItems,
        ).toHaveBeenCalledWith([]);
      });

      expect(mockFetchMediaAssets).toHaveBeenCalledTimes(1);
    });

    it("accumulates assets across multiple encounter windows until pageSize is reached", async () => {
      const mockFetchMediaAssets = jest.fn().mockImplementation((request) => {
        const from = request?.from || 0;

        return Promise.resolve({
          data: {
            hits: Array(5)
              .fill(null)
              .map((_, i) => ({
                id: `enc${from + i}`,
                access: "read",
                mediaAssets: [
                  { id: `asset${from + i}`, url: `http://x/${from + i}.jpg` },
                ],
              })),
          },
        });
      });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(globalEncounterFormStore.setCurrentPageItems).toHaveBeenCalled();
      });

      const items =
        globalEncounterFormStore.setCurrentPageItems.mock.calls[
          globalEncounterFormStore.setCurrentPageItems.mock.calls.length - 1
        ][0];

      expect(items).toHaveLength(20);
    });

    it("respects assetOffset when current encounter has remaining media assets", async () => {
      globalEncounterFormStore.assetOffset = 1;

      const mockFetchMediaAssets = jest
        .fn()
        .mockResolvedValueOnce({
          data: {
            hits: [
              {
                id: "enc1",
                access: "read",
                mediaAssets: [
                  { id: "asset0", url: "http://x/0.jpg" },
                  { id: "asset1", url: "http://x/1.jpg" },
                  { id: "asset2", url: "http://x/2.jpg" },
                ],
              },
            ],
          },
        })
        .mockResolvedValueOnce({
          data: {
            hits: [],
          },
        });

      jest
        .spyOn(useFilterEncountersWithMediaAssetsHook, "default")
        .mockReturnValue({ fetchMediaAssets: mockFetchMediaAssets });

      renderWithProviders();

      fireEvent.click(screen.getByText("TriggerGalleryPagination"));

      await waitFor(() => {
        expect(globalEncounterFormStore.setCurrentPageItems).toHaveBeenCalled();
      });

      const items =
        globalEncounterFormStore.setCurrentPageItems.mock.calls[
          globalEncounterFormStore.setCurrentPageItems.mock.calls.length - 1
        ][0];

      expect(items[0]).toMatchObject({ id: "asset1", encounterId: "enc1" });
      expect(globalEncounterFormStore.setAssetOffset).toHaveBeenCalled();
    });
  });
});
