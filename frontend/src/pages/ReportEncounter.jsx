import React, { useContext, useState } from "react";
import { Container, Row, Col, Form, Button } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";
import { Alert } from "react-bootstrap";
import MainButton from "../components/MainButton";

export default function ReportEncounter() {

    const themeColor = useContext(ThemeColorContext);
    const encounterCategories = ["photos", "date and time", "place", "species", "additional comments", "followup information"];
    const menu = encounterCategories.map((category, index) => {
        return { 
            id: index,
            title: category };
    }
    );

    const [selectedCategory, setSelectedCategory ] = useState(0);

    return (
        <Container>
            <Row >
                <h3 className="pt-4">Report an Encounter</h3>
                <p>
                    Please use the online form below to record the details of your encounter. Be as accurate and specific as possible.                     </p>
                <Alert
                    variant="warning"
                    onClose={() => { }}
                    dismissible
                >
                    <i className="bi bi-info-circle-fill" style={{ marginRight: '8px', color: '#7b6a00' }}></i>
                    You are not signed in. If you want this encounter associated with your account, be sure to{' '}
                    <a href="/signin" style={{ color: '#337ab7', textDecoration: 'underline' }}>
                        sign in!
                    </a>
                </Alert>
            </Row>
            <Row>
                <Alert
                    variant="light"
                    className="d-inline-block p-2"
                    style={{ backgroundColor: '#fff5f5', color: '#dc3545', width: "auto" }}>
                    <strong>* required fields</strong>
                </Alert>
            </Row>

            <Row >
                <Col className="col-lg-5 col-md-6 col-sm-12 col-12 ps-0">

                    <div
                        style={{
                            backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.5)),url(${process.env.PUBLIC_URL}/images/report_an_encounter.png)`,
                            backgroundSize: 'cover',
                            backgroundPosition: 'center',
                            borderRadius: '25px',
                            padding: "20px",
                            height: '470px',
                            width: '350px',
                            color: 'white',
                            marginBottom: '20px',
                        }}
                    >
                        {
                            menu.map(data => {
                                console.log(data);
                                console.log(selectedCategory);
                                return <div
                                    className="d-flex justify-content-between"
                                    style={{
                                        padding: '10px',
                                        marginTop: '10px',
                                        fontSize: '20px',
                                        fontWeight: '500',
                                        cursor: 'pointer',
                                        borderRadius: '10px',
                                        backgroundColor: selectedCategory === data.id ? "rgba(255,255,255,0.5)": 'transparent',

                                    }}
                                    onClick={() => setSelectedCategory(data.id)}
                                    >
                                    {data.title}<i class="bi bi-chevron-right" style={{ fontSize: "14px", fontWeight: "500" }}></i>
                                </div>

                            })

                        }

                        <MainButton
                            backgroundColor={themeColor.wildMeColors.cyan600}
                            color={themeColor.defaultColors.white}
                            shadowColor={themeColor.defaultColors.white}
                            style={{
                                width: "calc(100% - 20px)",
                                fontSize: "20px",
                                marginTop: "20px",
                                marginBottom: "20px",
                            }}

                        >
                            Submit Encounter
                        </MainButton>

                    </div>
                </Col>
                <Col
                    className="col-lg-7 col-md-6 col-sm-12 col-12 h-100"
                    style={{
                        // padding: '20px',
                        // backgroundColor: '#f8f9fa',
                        // borderRadius: '25px',
                        maxHeight: '470px',
                        overflow: 'auto',
                    }}
                >
                    <Form>
                        <Form.Group controlId="0">
                            <Form.Label>Location</Form.Label>
                            <Form.Control type="text" placeholder="Enter location" />
                            <Form.Label>Location</Form.Label>
                            <Form.Control type="text" placeholder="Enter location" />
                            <Form.Label>Location</Form.Label>
                            <Form.Control type="text" placeholder="Enter location" />
                            <Form.Label>Location</Form.Label>
                            <Form.Control type="text" placeholder="Enter location" />
                        </Form.Group>
                        <Form.Group controlId="1">
                            <Form.Label>Date</Form.Label>
                            <Form.Control type="date" placeholder="Enter date" />
                            <Form.Label>Date</Form.Label>
                            <Form.Control type="date" placeholder="Enter date" />
                            <Form.Label>Date</Form.Label>
                            <Form.Control type="date" placeholder="Enter date" />
                            <Form.Label>Date</Form.Label>
                            <Form.Control type="date" placeholder="Enter date" />
                        </Form.Group>
                        <Form.Group controlId="2">
                            <Form.Label>Time</Form.Label>
                            <Form.Control type="time" placeholder="Enter time" />
                            <Form.Label>Time</Form.Label>
                            <Form.Control type="time" placeholder="Enter time" />
                            <Form.Label>Time</Form.Label>
                            <Form.Control type="time" placeholder="Enter time" />
                            <Form.Label>Time</Form.Label>
                            <Form.Control type="time" placeholder="Enter time" />
                        </Form.Group>
                        <Form.Group controlId="3">
                            <Form.Label>Species</Form.Label>
                            <Form.Control type="text" placeholder="Enter species" />
                            <Form.Label>Species</Form.Label>
                            <Form.Control type="text" placeholder="Enter species" />
                            <Form.Label>Species</Form.Label>
                            <Form.Control type="text" placeholder="Enter species" />
                            <Form.Label>Species</Form.Label>
                            <Form.Control type="text" placeholder="Enter species" />
                        </Form.Group>
                        <Form.Group controlId="4">
                            <Form.Label>Number of animals</Form.Label>
                            <Form.Control type="number" placeholder="Enter number of animals" />
                            <Form.Label>Number of animals</Form.Label>
                            <Form.Control type="number" placeholder="Enter number of animals" />
                            <Form.Label>Number of animals</Form.Label>
                            <Form.Control type="number" placeholder="Enter number of animals" />
                            <Form.Label>Number of animals</Form.Label>
                            <Form.Control type="number" placeholder="Enter number of animals" />
                        </Form.Group>
                        <Form.Group controlId="5">
                            <Form.Label>Behavior</Form.Label>
                            <Form.Control type="text" placeholder="Enter behavior" />
                            <Form.Label>Behavior</Form.Label>
                            <Form.Control type="text" placeholder="Enter behavior" />
                            <Form.Label>Behavior</Form.Label>
                            <Form.Control type="text" placeholder="Enter behavior" />
                            <Form.Label>Behavior</Form.Label>
                            <Form.Control type="text" placeholder="Enter behavior" />
                        </Form.Group>
                    </Form>
                </Col>

            </Row>
        </Container>
    );
}