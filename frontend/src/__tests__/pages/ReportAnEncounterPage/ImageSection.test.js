import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ImageSection } from "../ImageSection";
import { Provider } from "mobx-react";
import userEvent from "@testing-library/user-event";
import ThemeContext from "../../ThemeColorProvider";
import { IntlProvider } from "react-intl";
import Store from "../../store";

jest.mock("../../models/useGetSiteSettings", () => () => ({
  data: { maximumMediaSizeMegabytes: 40 },
}));

const mockStore = new Store();

const renderComponent = () => {
  return render(
    <Provider store={mockStore}>
      <IntlProvider locale="en" messages={{}}>
        <ThemeContext.Provider
          value={{
            primaryColors: { primary500: "#000" },
            statusColors: { red800: "red", red500: "red" },
            defaultColors: { white: "#fff" },
            wildMeColors: { cyan700: "#00bcd4" },
          }}
        >
          <ImageSection store={mockStore} />
        </ThemeContext.Provider>
      </IntlProvider>
    </Provider>,
  );
};

describe("ImageSection Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getByText("PHOTOS_SECTION")).toBeInTheDocument();
  });

  test("displays an alert if user is anonymous", () => {
    mockStore.isHumanLocal = false;
    renderComponent();
    expect(screen.getByText("ANON_UPLOAD_IMAGE_WARNING")).toBeInTheDocument();
  });

  test("allows users to select files", async () => {
    renderComponent();
    const fileInput = screen.getByLabelText(/BROWSE/i);

    const file = new File(["dummy content"], "example.jpg", {
      type: "image/jpeg",
    });
    await userEvent.upload(fileInput, file);

    await waitFor(() =>
      expect(mockStore.imagePreview.length).toBeGreaterThan(0),
    );
  });

  test("prevents unsupported file types", async () => {
    renderComponent();
    const fileInput = screen.getByLabelText(/BROWSE/i);
    const file = new File(["content"], "example.txt", { type: "text/plain" });

    await userEvent.upload(fileInput, file);
    await waitFor(() => expect(mockStore.imagePreview.length).toBe(0));
  });

  test("removes files from the preview list", async () => {
    renderComponent();
    const fileInput = screen.getByLabelText(/BROWSE/i);
    const file = new File(["dummy content"], "example.jpg", {
      type: "image/jpeg",
    });
    await userEvent.upload(fileInput, file);

    await waitFor(() =>
      expect(screen.getByText("example.jpg")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByRole("button", { name: /remove/i }));
    await waitFor(() =>
      expect(screen.queryByText("example.jpg")).not.toBeInTheDocument(),
    );
  });

  test("shows file size exceeded warning", async () => {
    renderComponent();
    const fileInput = screen.getByLabelText(/BROWSE/i);
    const largeFile = new File(["large file content"], "large.jpg", {
      size: 50 * 1024 * 1024,
      type: "image/jpeg",
    });

    await userEvent.upload(fileInput, largeFile);
    await waitFor(() =>
      expect(screen.getByText("FILE_SIZE_EXCEEDED")).toBeInTheDocument(),
    );
  });
});
