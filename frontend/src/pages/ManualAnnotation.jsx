import React, { useState, useContext, useRef, useEffect } from "react";
import Select from "react-select";
import Form from "react-bootstrap/Form";
import { FormattedMessage } from "react-intl";
import Container from "react-bootstrap/Container";
import MainButton from "../components/MainButton";
import ThemeColorContext from "../ThemeColorProvider";
import ResizableRotatableRect from "../components/ResizableRotatableRect";
import useGetSiteSettings from "../models/useGetSiteSettings";
import { useSearchParams } from "react-router-dom";
import AnnotationSuccessful from "../components/AnnotationSuccessful";
import useCreateAnnotation from "../models/encounters/useCreateAnnotation";
import calculateFinalRect from "../models/js/calculateFinalRect";
import calculateScaleFactor from "../models/js/calculateScaleFactor";
import AddAnnotationModal from "../components/AddAnnotationModal";

export default function ManualAnnotation() {
    const [searchParams] = useSearchParams();
    const assetId = searchParams.get("assetId");
    const encounterId = searchParams.get("encounterId");
    const theme = useContext(ThemeColorContext);
    const imgRef = useRef(null);
    const [value, setValue] = useState(0);
    const [incomplete, setIncomplete] = useState(false);
    const [data, setData] = useState({
        width: 100,
        height: 100,
        url: "",
    });

    const { createAnnotation, loading, error, submissionDone, responseData } =
        useCreateAnnotation();

    const [showModal, setShowModal] = useState(false);
    const [scaleFactor, setScaleFactor] = useState({ x: 1, y: 1 });
    const [ia, setIa] = useState(null);
    const [viewpoint, setViewpoint] = useState(null);
    const [isDrawing, setIsDrawing] = useState(false);
    const [drawStatus, setDrawStatus] = useState("DRAW");
    const [rect, setRect] = useState({
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        rotation: 0,
    });

    const { data: siteData } = useGetSiteSettings();
    const iaOptions = siteData?.iaClass?.map((iaClass) => ({
        value: iaClass,
        label: iaClass,
    }));
    const viewpointOptions = siteData?.annotationViewpoint?.map((viewpoint) => ({
        value: viewpoint,
        label: viewpoint,
    }));

    const getMediaAssets = async () => {
        try {
            const response = await fetch(`/api/v3/media-assets/${assetId}`);
            const data = await response.json();
            setData(data);
        } catch (error) {
            alert("Error fetching media assets", error);
        }
    };

    useEffect(() => {
        if (isDrawing) {
            setDrawStatus("DRAWING");
        } else if (rect.width > 0 && rect.height > 0) {
            setDrawStatus("DELETE");
        } else {
            setDrawStatus("DRAW");
        }
    }, [isDrawing, rect]);

    useEffect(() => {
        if (error) {
            setShowModal(true);
        }
    }, [error]);

    useEffect(() => {
        const handleImageLoad = () => {
            if (imgRef.current) {
                const factor = calculateScaleFactor(data.width, data.height, imgRef.current.clientWidth, imgRef.current.clientHeight);
                setScaleFactor(factor);
            }
        };

        const imgElement = imgRef.current;
        if (imgElement && imgElement.complete) {
            handleImageLoad();
        } else if (imgElement) {
            imgElement.addEventListener("load", handleImageLoad);
        }

        return () => {
            if (imgElement) {
                imgElement.removeEventListener("load", handleImageLoad);
            }
        };
    }, [data]);

    useEffect(() => {
        if (assetId && encounterId) {
            const fetchData = async () => {
                await getMediaAssets();
            };
            fetchData();
        }
    }, [assetId, encounterId]);

    useEffect(() => {
        const handleMouseUp = () => setIsDrawing(false);
        window.addEventListener("mouseup", handleMouseUp);

        return () => {
            window.removeEventListener("mouseup", handleMouseUp)
        };
    }, []);

    const handleMouseDown = (e) => {
        if (!imgRef.current || drawStatus === "DELETE") return;

        const { left, top } = imgRef.current.getBoundingClientRect();
        setRect({
            x: e.clientX - left,
            y: e.clientY - top,
            width: 0,
            height: 0,
            rotation: value,
        });
        setIsDrawing(true);
    };

    const handleMouseMove = (e) => {
        if (!imgRef.current || drawStatus === "DELETE") return;

        const { left, top } = imgRef.current.getBoundingClientRect();
        const mouseX = e.clientX - left;
        const mouseY = e.clientY - top;

        if (isDrawing) {
            setRect((prevRect) => ({
                ...prevRect,
                width: mouseX - prevRect.x,
                height: mouseY - prevRect.y,
                rotation: value,
            }));
        }
    };

    const handleMouseUp = () => {
        if (!imgRef.current || drawStatus === "DELETE") return;
        setIsDrawing(false);
    };

    return (
        <Container>
            {submissionDone ? (
                <AnnotationSuccessful
                    annotationId={responseData?.id}
                    encounterId={encounterId}
                    rect={calculateFinalRect(rect, scaleFactor, value)}
                    imageData={data}
                />
            ) : (
                <>
                    <h4 className="mt-3 mb-3">
                        <FormattedMessage id="ADD_ANNOTATIONS" />
                    </h4>
                    <Form className="d-flex flex-row">
                        <Form.Group controlId="formBasicEmail" className="me-3">
                            <Form.Label>
                                <FormattedMessage id="FILTER_IA_CLASS" />*
                            </Form.Label>
                            <Select
                                options={iaOptions}
                                className="basic-multi-select"
                                classNamePrefix="select"
                                menuPlacement="auto"
                                menuPortalTarget={document.body}
                                value={ia}
                                styles={{
                                    container: (provided) => ({
                                        ...provided,
                                        width: "200px",
                                    }),
                                }}
                                onChange={(selected) => {
                                    setIa(selected);
                                }}
                            />
                        </Form.Group>
                        <Form.Group controlId="formBasicEmail">
                            <Form.Label>
                                <FormattedMessage id="FILTER_VIEWPOINT" />*
                            </Form.Label>
                            <Select
                                options={viewpointOptions}
                                className="basic-multi-select"
                                classNamePrefix="select"
                                menuPlacement="auto"
                                menuPortalTarget={document.body}
                                value={viewpoint}
                                styles={{
                                    container: (provided) => ({
                                        ...provided,
                                        width: "200px",
                                    }),
                                }}
                                onChange={(selected) => {
                                    setViewpoint(selected);
                                }}
                            />
                        </Form.Group>
                    </Form>
                    <div
                        className="d-flex flex-column"
                        style={{
                            maxWidth: "100%",
                            height: "auto",
                            padding: "1em",
                            marginTop: "1em",
                            borderRadius: "10px",
                            boxShadow: "0 0 10px rgba(0, 0, 0, 0.2)",
                        }}
                    >
                        <div className="d-flex justify-content-between">
                            <h6>
                                <FormattedMessage id="DRAW_ANNOTATION" />
                            </h6>
                            <div
                                style={{
                                    cursor: "pointer",
                                    color: theme.primaryColors.primary500,
                                }}
                                onClick={() => {
                                    if (drawStatus === "DELETE") {
                                        setRect({
                                            x: 0,
                                            y: 0,
                                            width: 0,
                                            height: 0,
                                        });
                                    } else if (drawStatus === "DRAW") {
                                        setDrawStatus("DRAWING");
                                    }
                                }}
                            >
                                <FormattedMessage id={drawStatus} />
                                {drawStatus === "DELETE" && (
                                    <i className="bi bi-trash ms-2"></i>
                                )}
                            </div>
                        </div>
                        <div
                            id="image-container"
                            onMouseDown={handleMouseDown}
                            onMouseMove={handleMouseMove}
                            onMouseUp={handleMouseUp}
                            style={{
                                width: "100%",
                                marginTop: "1rem",
                                position: "relative",
                            }}
                        >
                            <img
                                ref={imgRef}
                                src={data.url}
                                alt="annotationimages"
                                style={{
                                    width: "100%",
                                    height: "auto",
                                    objectFit: "contain",
                                    position: "absolute",
                                    top: 0,
                                    left: 0,
                                }}
                            />

                            <ResizableRotatableRect
                                rect={rect}
                                imgHeight={imgRef.current?.height}
                                imgWidth={imgRef.current?.width}
                                setRect={setRect}
                                setValue={setValue}
                                drawStatus={drawStatus}
                            />
                        </div>
                    </div>
                    <MainButton
                        noArrow={true}
                        style={{ marginTop: "1em" }}
                        backgroundColor={theme.primaryColors.primary500}
                        borderColor={theme.primaryColors.primary500}
                        color={theme.defaultColors.white}
                        loading={loading}
                        onClick={async () => {
                            try {
                                if (!ia || !viewpoint || !rect.width || !rect.height) {
                                    setShowModal(true);
                                    setIncomplete(true);
                                    return;
                                } else {
                                    setIncomplete(false);
                                    const { x, y, width, height, rotation } = calculateFinalRect(rect, scaleFactor, value);
                                    await createAnnotation({
                                        encounterId,
                                        assetId,
                                        ia,
                                        viewpoint,
                                        x,
                                        y,
                                        width,
                                        height,
                                        rotation,
                                    });
                                }
                            } catch (error) {
                                alert("Error creating annotation", error);
                                setShowModal(true);
                            }
                        }}
                    >
                        <FormattedMessage id="SAVE_ANNOTATION" />
                    </MainButton>
                    <AddAnnotationModal
                        showModal={showModal}
                        setShowModal={setShowModal}
                        incomplete={incomplete}
                        error={error}
                    />
                </>
            )}
        </Container>
    );
}
