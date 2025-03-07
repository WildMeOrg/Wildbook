import { renderHook, act } from "@testing-library/react";
import axios from "axios";
import usePostHeaderQuickSearch from "../../../models/usePostHeaderQuickSearch";

jest.mock("axios");

describe("usePostHeaderQuickSearch", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("returns empty results when search value is empty", () => {
    const { result } = renderHook(() => usePostHeaderQuickSearch(""));

    expect(result.current.searchResults).toEqual([]);
    expect(result.current.loading).toBe(false);
  });

  //   test("sets loading to true when searching", async () => {
  //     axios.post.mockResolvedValue({ data: { hits: [{ id: "123", names: ["John Doe"] }] } });

  //     const { result } = renderHook(() => usePostHeaderQuickSearch("John"));

  //     expect(result.current.loading).toBe(false);

  //     await act(async () => {
  //       await new Promise((resolve) => setTimeout(resolve, 300));
  //     });

  //     expect(result.current.loading).toBe(true);
  //   });

  test("fetches and updates search results", async () => {
    const mockData = { data: { hits: [{ id: "123", names: ["John Doe"] }] } };
    axios.post.mockResolvedValue(mockData);

    const { result } = renderHook(() => usePostHeaderQuickSearch("John"));

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 300));
    });

    expect(axios.post).toHaveBeenCalledWith(
      "/api/v3/search/individual?size=10",
      expect.any(Object),
    );
    expect(result.current.searchResults).toEqual(mockData.data.hits);
    expect(result.current.loading).toBe(false);
  });

  test("handles API errors", async () => {
    axios.post.mockRejectedValue(new Error("API error"));

    const { result } = renderHook(() => usePostHeaderQuickSearch("John"));

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 300));
    });

    expect(axios.post).toHaveBeenCalled();
    expect(result.current.searchResults).toEqual([]);
    expect(result.current.loading).toBe(false);
  });

  //   test("clears timeout on unmount", () => {
  //     jest.useFakeTimers();
  //     const { unmount } = renderHook(() => usePostHeaderQuickSearch("John"));
  //     unmount();
  //     expect(clearTimeout).toHaveBeenCalled();
  //     jest.useRealTimers();
  //   });
});
