import React, { useContext } from "react";
import { Container, Row, Col, Form, Button } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";

export default function ReportEncounter() {

    const themeColor = useContext(ThemeColorContext);
    return (
        <Container>
            <Row>
                <Col className="col-lg-5 col-md-6 col-sm-12 col-12 d-flex flex-column justify-content-center">
                    <h1>Report an Encounter</h1>
                    <p>
                        Please use the online form below to record the details of your encounter. Be as accurate and specific as possible.                     </p>
                    <div className="w-100"
                        style={{
                            backgroundColor: themeColor.statusColors.yellow100,
                            color: '#721c24',
                            padding: '20px',
                            marginBottom: '20px'
                        }}
                    >
                        Note
                        The Fields with ** are required fields
                    </div>
                    <img src={`${process.env.PUBLIC_URL}/images/report_an_encounter.png`}
                        className="img-fluid" alt="Report an encounter"
                        style={{ 
                            borderRadius: '25px',
                            padding: "-10px",
                        }}
                    ></img>
                </Col>
                <Col className="col-lg-7 col-md-6 col-sm-12 col-12">

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