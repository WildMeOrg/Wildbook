import React, { useState, useContext, useRef, useEffect } from "react";
import Select from "react-select";
import Form from "react-bootstrap/Form";
import { FormattedMessage } from "react-intl";
import Container from "react-bootstrap/Container";
import MainButton from "../components/MainButton";
import ThemeColorContext from "../ThemeColorProvider";
import Slider from "../components/Slider";
import ResizableRotatableRect from "../components/ResizableRotatableRect";

export default function ManualAnnotation() {
    const theme = useContext(ThemeColorContext);
    const imgRef = useRef(null);
    const [value, setValue] = useState(0);
    const [data, setData] = useState({
        width: 100,
        height: 100,
        url: ""
    });

    const [rect, setRect] = useState({
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        rotation: 0,
    });
    const [isDrawing, setIsDrawing] = useState(false);
    const [drawStatus, setDrawStatus] = useState("DRAW");

    const getMediaAssets = async () => {
        try {
            const response = await fetch("/api/v3/media-assets/329716");
            const data = await response.json();
            // console.log("++++++++++++++++++++++++++++", data);
            setData(data);
        } catch (error) {
            // console.error("-------------------------------", error);
        }
    };

    console.log("rect", rect);

    const [scaleFactor, setScaleFactor] = useState({ x: 1, y: 1 });

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
        if (imgRef.current) {
            const naturalWidth = data.width;
            const naturalHeight = data.height;
            const displayWidth = imgRef.current.clientWidth;
            const displayHeight = imgRef.current.clientHeight;

            const scaleX = displayWidth / naturalWidth;
            const scaleY = displayHeight / naturalHeight;

            setScaleFactor({ x: scaleX, y: scaleY });
        }
    }, [data, imgRef]);

    useEffect(() => {
        const fetchData = async () => {
            await getMediaAssets();
        };
        fetchData();
    }, []);

    useEffect(() => {
        const handleMouseUp = () => setIsDrawing(false);
        window.addEventListener("mouseup", handleMouseUp);
        return () => window.removeEventListener("mouseup", handleMouseUp);
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
            <h4 className="mt-3 mb-3">
                <FormattedMessage id="ADD_ANNOTATIONS" />
            </h4>
            <Form className="d-flex flex-row">
                <Form.Group controlId="formBasicEmail" className="me-3">
                    <Form.Label>
                        <FormattedMessage id="IA_CLASS" />*
                    </Form.Label>
                    <Select
                        isMulti={true}
                        options={[]}
                        className="basic-multi-select"
                        classNamePrefix="select"
                        menuPlacement="auto"
                        menuPortalTarget={document.body}
                        value={[]}
                        onChange={() => { }}
                    />
                </Form.Group>
                <Form.Group controlId="formBasicEmail">
                    <Form.Label>
                        <FormattedMessage id="VIEWPOINT" />*
                    </Form.Label>
                    <Select
                        isMulti={true}
                        options={[]}
                        className="basic-multi-select"
                        classNamePrefix="select"
                        menuPlacement="auto"
                        menuPortalTarget={document.body}
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
                        <i className="bi bi-trash ms-2"></i>
                    </div>
                </div>
                <div id="image-container"
                    onMouseDown={handleMouseDown}
                    onMouseMove={handleMouseMove}
                    onMouseUp={handleMouseUp}
                    style={{
                        width: "100%",
                        marginTop: "1rem",
                        position: "relative", // Key to enable stacking
                    }}
                >
                    <img
                        ref={imgRef}
                        src={"http://frontend.scribble.com/wildbook_data_dir/a/1/a1e8b85b-1a22-4ecd-aa8d-59aa93b3322c/48f0d20d-7104-4e8b-9fb6-1d806a831af3-master.jpg"}
                        alt="annotationimages"
                        style={{
                            width: "100%",
                            // maxHeight: "600px",
                            height: "auto",
                            objectFit: "contain",
                            position: "absolute", // Ensure stacking
                            top: 0,
                            left: 0,
                        }}
                    />

                    <ResizableRotatableRect
                        rect={rect}
                        imgHeight={imgRef.current?.height}
                        imgWidth={imgRef.current?.width}
                        setRect={setRect}
                        angle={value}
                        drawStatus={drawStatus}
                    />
                </div>

                <Slider
                    min={0}
                    max={360}
                    step={1}
                    setValue={setValue}
                />
            </div>

            <MainButton
                noArrow={true}
                style={{ marginTop: "1em" }}
                backgroundColor="lightblue"
                borderColor="#303336"
                onClick={async () => {
                    try {
                        console.log("111");
                        const response = await fetch("/api/v3/annotations/");
                        const data = await response.json();
                        // console.log("++++++++++++++++++++++++++++", data);
                        setData(data);
                    } catch (error) {
                        // console.error("-------------------------------", error);
                    }

                }}
            >
                <FormattedMessage id="SAVE_ANNOTATION" />
            </MainButton>
        </Container>
    );
}



