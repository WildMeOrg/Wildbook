import React, { useContext } from "react";
import { Container, Row, Col, Form, Button } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";
import { Alert } from "react-bootstrap";
import MainButton from "../components/MainButton";

export default function ReportEncounter() {

    const themeColor = useContext(ThemeColorContext);
    const menu = [
        { title: "Photos" },
        { title: "Videos" },
        { title: "Text" },
        { title: "Location" },
        { title: "Date" },
        { title: "Time" },
        { title: "Species" },
    ]
    return (
        <Container>
            <Row >
                <h1>Report an Encounter</h1>
                <p>
                    Please use the online form below to record the details of your encounter. Be as accurate and specific as possible.                     </p>
                <Alert
                    variant="warning"
                    onClose={() => {}}
                    dismissible
                >
                    <i className="bi bi-info-circle-fill" style={{ marginRight: '8px', color: '#7b6a00' }}></i>
                    You are not signed in. If you want this encounter associated with your account, be sure to{' '}
                    <a href="/signin" style={{ color: '#337ab7', textDecoration: 'underline' }}>
                        sign in!
                    </a>
                </Alert>
            </Row>

            <Row >
                <Col className="col-lg-4 col-md-6 col-sm-12 col-12">
                    <Alert variant="light" className="d-inline-block p-2" style={{ backgroundColor: '#fff5f5', color: '#dc3545' }}>
                        <strong>*</strong> required fields
                    </Alert>
                    <div
                        style={{
                            backgroundImage: `url(${process.env.PUBLIC_URL}/images/report_an_encounter.png)`,
                            backgroundSize: 'cover',
                            backgroundPosition: 'center',
                            borderRadius: '25px',
                            padding: "10px",
                            height: '500px',
                            width: '350px',
                            color: 'white',
                        }}
                    >
                        {
                            menu.map(data => {
                                return <div
                                    className="d-flex justify-content-between"
                                    style={{
                                        padding: '15px',
                                        fontSize: '20px',
                                        fontWeight: '500',

                                    }}>
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
                        }}
                        
                       >
                            Submit Encounter
                        </MainButton> 

                    </div>
                </Col>
                <Col className="col-lg-8 col-md-6 col-sm-12 col-12">
                    <Form>
                        <Form.Group controlId="formBasicEmail">
                            <Form.Label>Location</Form.Label>
                            <Form.Control type="text" placeholder="Enter location" />
                        </Form.Group>
                        <Form.Group controlId="formBasicPassword">
                            <Form.Label>Date</Form.Label>
                            <Form.Control type="date" placeholder="Enter date" />
                        </Form.Group>
                        <Form.Group controlId="formBasicPassword">
                            <Form.Label>Time</Form.Label>
                            <Form.Control type="time" placeholder="Enter time" />
                        </Form.Group>
                        <Form.Group controlId="formBasicPassword">
                            <Form.Label>Species</Form.Label>
                            <Form.Control type="text" placeholder="Enter species" />
                        </Form.Group>
                        <Form.Group controlId="formBasicPassword">
                            <Form.Label>Number of animals</Form.Label>
                            <Form.Control type="number" placeholder="Enter number of animals" />
                        </Form.Group>
                        <Form.Group controlId="formBasicPassword">
                            <Form.Label>Behavior</Form.Label>
                            <Form.Control type="text" placeholder="Enter behavior" />
                        </Form.Group>
                        <Form.Group controlId="formBasicPassword">
                            <Form.Label>Description</Form.Label>
                            <Form.Control type="text" placeholder="Enter description" />
                        </Form.Group>
                        <Form.Group controlId="formBasicPassword">
                            <Form.Label>Photos</Form.Label>
                            <Form.Control type="file" placeholder="Enter photos" />
                        </Form.Group>
                        <Button variant="primary" type="submit">
                            Submit
                        </Button>
                    </Form>
                </Col>

            </Row>
        </Container>
    );
}