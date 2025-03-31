import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import ImageLabelFilter from "../../../components/filterFields/ImageLabelFilter";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
  removeFilterByFilterKey: jest.fn(),
};

const mockData = {
  keyword: ["Bird", "Animal"],
  annotationViewpoint: ["Front", "Side"],
  iaClass: ["Class A", "Class B"],
  labeledKeyword: {
    exampleKey: ["Label 1", "Label 2"],
  },
};

describe("ImageLabelFilter Component", () => {
  beforeEach(() => {
    mockStore.formFilters = [];
    jest.clearAllMocks();
  });

  const renderComponent = () => {
    renderWithProviders(<ImageLabelFilter data={mockData} store={mockStore} />);
  };

  test("renders component labels correctly", () => {
    renderComponent();

    expect(screen.getByText("FILTER_IMAGE_LABEL")).toBeInTheDocument();
    expect(
      screen.getByText(/FILTER_HAS_AT_LEAST_ONE_ASSOCIATED_PHOTO_OR_VIDEO/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/FILTER_KEYWORDS_DESC/i)).toBeInTheDocument();
  });

  test("handles checkbox interaction correctly", () => {
    renderComponent();

    const mediaAssetCheckbox = screen.getByLabelText(
      /FILTER_HAS_AT_LEAST_ONE_ASSOCIATED_PHOTO_OR_VIDEO/,
    );

    fireEvent.click(mediaAssetCheckbox);

    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "numberMediaAssets",
      "filter",
      { range: { numberMediaAssets: { gte: 1 } } },
      "Number Media Assets",
    );
  });

  test("renders keywords options correctly", () => {
    renderComponent();
    const dropdown = screen.getAllByText("Select one or more")[0];
    fireEvent.mouseDown(dropdown);
    expect(screen.getByText("Bird")).toBeInTheDocument();
    expect(screen.getByText("Animal")).toBeInTheDocument();
  });

  test("handles 'AND' operator checkbox correctly", () => {
    renderComponent();

    const andOperatorCheckbox = document.querySelectorAll(
      'input[type="checkbox"]',
    )[0];

    fireEvent.click(andOperatorCheckbox);

    expect(mockStore.removeFilterByFilterKey).not.toHaveBeenCalled(); // initial click just toggles local state
  });
});
