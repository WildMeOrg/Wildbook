import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";

jest.mock("swiper/css", () => ({}), { virtual: true });
jest.mock("react-intl", () => ({
  FormattedMessage: ({ defaultMessage, id }) => (
    <span>{defaultMessage ?? id}</span>
  ),
}));
jest.mock("mobx-react-lite", () => ({ observer: (c) => c }));

import ImageGalleryModal from "../../../pages/SearchPages/searchResultTabs/ImageGalleryModal";

function makeImageStore(overrides = {}) {
  return {
    showAnnotations: true,
    setShowAnnotations: jest.fn(),
    ...overrides,
  };
}

function setImgDims(el, { w = 500, h = 250 } = {}) {
  Object.defineProperty(el, "clientWidth", { value: w, configurable: true });
  Object.defineProperty(el, "clientHeight", { value: h, configurable: true });
}

describe("ImageGalleryModal", () => {
  let origOpen = window.open;
  let origCreate = document.createElement;

  afterAll(() => {
    window.open = origOpen;
    document.createElement = origCreate;
  });

  test("not open or no assets -> null", () => {
    const s = makeImageStore();
    const { rerender } = render(
      <ImageGalleryModal open={false} assets={[]} imageStore={s} />,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    rerender(<ImageGalleryModal open assets={[]} imageStore={s} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  test("basic render", () => {
    const s = makeImageStore();
    const assets = [
      { id: "1", url: "u1", width: 1000, height: 500 },
      { id: "2", url: "u2", width: 800, height: 400 },
    ];
    render(<ImageGalleryModal open assets={assets} index={0} imageStore={s} />);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("1/2")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /asset-1/i })).toHaveAttribute(
      "src",
      "u1",
    );
  });

  test("close", () => {
    const s = makeImageStore();
    const fn = jest.fn();
    const assets = [{ id: "1", url: "u1", width: 1, height: 1 }];
    render(
      <ImageGalleryModal
        open
        assets={assets}
        index={0}
        imageStore={s}
        onClose={fn}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /close/i }));
    expect(fn).toHaveBeenCalled();
  });

  test("download", () => {
    const s = makeImageStore();
    const aClick = jest.fn();
    const aNode = { click: aClick };
    document.createElement = jest.fn((t) =>
      t === "a" ? aNode : origCreate.call(document, t),
    );

    const assets = [{ id: "42", url: "u42", width: 1, height: 1 }];
    render(<ImageGalleryModal open assets={assets} index={0} imageStore={s} />);
    fireEvent.click(screen.getByRole("button", { name: /download/i }));

    expect(aNode.href).toBe("u42");
    expect(aNode.download).toBe("encounter-image-42.jpg");
    expect(aClick).toHaveBeenCalled();
  });

  test("toggle annotations", () => {
    const s = makeImageStore({ showAnnotations: false });
    const assets = [{ id: "1", url: "u1", width: 1, height: 1 }];
    render(<ImageGalleryModal open assets={assets} index={0} imageStore={s} />);
    const cb = screen.getByRole("checkbox", { name: /SHOW_ANNOTATIONS/i });
    fireEvent.click(cb);
    expect(s.setShowAnnotations).toHaveBeenCalledWith(true);
  });

  test("scaled rects", () => {
    const s = makeImageStore({ showAnnotations: true });
    const assets = [{ id: "1", url: "u1", width: 1000, height: 500 }];
    const rects = [
      {
        x: 20,
        y: 40,
        width: 100,
        height: 50,
        rotation: 0,
        annotationId: "a",
        encounterId: "e",
      },
    ];
    const { container } = render(
      <ImageGalleryModal
        open
        assets={assets}
        index={0}
        rects={rects}
        imageStore={s}
      />,
    );
    const img = screen.getByRole("img", { name: /asset-1/i });
    setImgDims(img);
    act(() => fireEvent.load(img));
    const el = container.querySelector("#image-modal-image .position-absolute");
    const st = el.getAttribute("style");
    expect(st).toContain("left: 10px");
    expect(st).toContain("top: 20px");
    expect(st).toContain("width: 50px");
    expect(st).toContain("height: 25px");
  });

  test("rect external link", () => {
    const s = makeImageStore({ showAnnotations: true });
    window.open = jest.fn();
    const assets = [{ id: "1", url: "u1", width: 1000, height: 500 }];
    const rects = [
      {
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        rotation: 0,
        annotationId: "a",
        encounterId: "E123",
      },
    ];
    const { container } = render(
      <ImageGalleryModal
        open
        assets={assets}
        index={0}
        rects={rects}
        imageStore={s}
      />,
    );
    const img = screen.getByRole("img", { name: /asset-1/i });
    setImgDims(img);
    act(() => fireEvent.load(img));
    const btn = container.querySelector(
      "#image-modal-image .position-absolute .d-flex.align-items-center",
    );
    fireEvent.click(btn);
    expect(window.open).toHaveBeenCalledWith(
      "/react/encounter?number=E123",
      "_blank",
    );
  });
});
