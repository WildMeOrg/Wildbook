import React, { useEffect } from "react";
import { Swiper, SwiperSlide } from "swiper/react";
import "swiper/css";

export default function ImageModal({ open, onClose, assets = [], index = 0, setIndex }) {
    useEffect(() => {
        if (!open) return;

        const onKey = (e) => {
            if (e.key === "Escape") onClose?.();
            if (e.key === "ArrowLeft") setIndex?.(p => Math.max(0, (typeof p === "number" ? p : index) - 1));
            if (e.key === "ArrowRight") setIndex?.(p => Math.min(assets.length - 1, (typeof p === "number" ? p : index) + 1));
        };
        const prevOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        window.addEventListener("keydown", onKey);
        return () => {
            document.body.style.overflow = prevOverflow || "";
            window.removeEventListener("keydown", onKey);
        };
    }, [open, onClose, setIndex, index, assets.length]);

    if (!open || !assets.length) return null;

    const safeIndex = Math.min(Math.max(index, 0), assets.length - 1);
    const a = assets[safeIndex] || {};

    return (
        <div
            role="dialog"
            aria-modal="true"
            className="position-fixed top-0 start-0 w-100 h-100"
            style={{
                background: "rgba(0, 0, 0, 0.6)",
                backdropFilter: "blur(2px)",
                WebkitBackdropFilter: "blur(2px)",
                color: "white",
                zIndex: 1080
            }}
            onClick={(e) => { if (e.target === e.currentTarget) onClose?.(); }}
        >
            <div
                className="container-fluid h-100 d-flex flex-column"
                style={{ minHeight: 0 }}
            >
                <div
                    className="d-flex align-items-center text-white"
                    style={{ flex: "0 0 56px" }}
                >
                    <span className="text-white-50 ms-2">{safeIndex + 1}/{assets.length}</span>
                    <div className="ms-auto d-flex gap-2 me-2">
                        <button className="btn btn-sm" onClick={onClose}>Close</button>
                    </div>
                </div>

                <div
                    className="d-flex"
                    style={{ flex: "1 1 auto", minHeight: 0 }}
                >
                    <div
                        className="d-flex flex-column flex-grow-1"
                        style={{ minWidth: 0, minHeight: 0 }}
                    >
                        <div
                            className="d-flex align-items-center justify-content-center"
                            style={{ flex: "1 1 auto", minHeight: 0, overflow: "hidden" }}
                        >
                            <img
                                src={a.url}
                                alt={a.filename ?? `asset-${a.id ?? safeIndex}`}
                                style={{
                                    maxWidth: "80%",
                                    maxHeight: "80%",
                                    objectFit: "contain",
                                    cursor: "zoom-in",
                                    display: "block",
                                }}
                                onClick={() => { }}
                            />
                        </div>

                        <div
                            style={{ flex: "0 0 110px" }}
                        >
                            <Swiper slidesPerView="auto" spaceBetween={8} style={{ padding: "8px 12px" }}>
                                {assets.map((item, i) => (
                                    <SwiperSlide key={item.uuid ?? item.id ?? i} style={{ width: 84 }}>
                                        <img
                                            src={item.url}
                                            alt={item.filename ?? ""}
                                            onClick={() => setIndex?.(i)}
                                            style={{
                                                width: 72,
                                                height: 72,
                                                objectFit: "cover",
                                                cursor: "pointer",
                                                borderRadius: 6,
                                                border: i === safeIndex ? "2px solid #fff" : "2px solid transparent",
                                            }}
                                        />
                                    </SwiperSlide>
                                ))}
                            </Swiper>
                        </div>
                    </div>

                    <aside
                        className="bg-white text-black ps-3 pe-3 pt-2"
                        style={{
                            flex: "0 0 360px",
                            minHeight: 0,
                            overflowY: "auto",
                        }}
                    >
                        <div className="d-flex align-items-center gap-2 mb-2">
                            {a.url ? (
                                <img src={a.url} alt="" className="rounded" style={{ width: 36, height: 36, objectFit: "cover" }} />
                            ) : (
                                <div className="rounded bg-light" style={{ width: 36, height: 36 }} />
                            )}
                            <div>
                                <div className="fw-semibold">{a.filename ?? `asset-${a.id ?? safeIndex}`}</div>
                                <div className="text-muted small">{a.date ?? ""}</div>
                            </div>
                        </div>

                        <div className="d-flex flex-wrap gap-2 mb-3">
                            {(a.tags ?? []).map((t) => (
                                <span key={t} className="badge text-bg-secondary">{t}</span>
                            ))}
                            <button className="btn btn-sm btn-outline-secondary">+ Add Tag</button>
                        </div>

                        <dl className="row g-2 mb-3">
                            <dt className="col-5">Encounter</dt>
                            <dd className="col-7 mb-0">{a.encounterId ?? "—"}</dd>
                            <dt className="col-5">Individual ID</dt>
                            <dd className="col-7 mb-0">{a.individualId ?? "—"}</dd>
                            <dt className="col-5">Location ID</dt>
                            <dd className="col-7 mb-0">{a.locationId ?? "—"}</dd>
                        </dl>

                        <div className="d-grid gap-2">
                            <button className="btn btn-outline-secondary btn-sm">Match Results</button>
                            <button className="btn btn-outline-secondary btn-sm">Visual Matcher</button>
                            <button className="btn btn-outline-secondary btn-sm">New Match</button>
                            <button className="btn btn-outline-secondary btn-sm">Add Annotation</button>
                            <button className="btn btn-outline-secondary btn-sm">Edit Annotation</button>
                        </div>
                    </aside>
                </div>
            </div>
        </div>
    );
}
