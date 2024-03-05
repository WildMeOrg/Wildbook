import React from 'react';
import { Container, Row, Col, Form, Button } from 'react-bootstrap';
import { useState } from 'react';
import BrutalismButton from '../components/BrutalismButton';
import Cookies from 'js-cookie';


function LoginPage() {

    const [rememberMe, setRememberMe] = useState(false);

    const handleSubmit = (event) => {
        event.preventDefault();
        console.log('Form submitted');
    
        if (rememberMe) {
          Cookies.set('rememberMe', 'true', { expires: 1/3 });
        } else {
          Cookies.set('rememberMe', 'true');
        }    
      };

  return (
    <Container fluid>
      <Row className="vh-100">
        <Col md={5} className="d-none d-md-block bg-image" style={{ backgroundImage: `url('/wildbook/react/forestWithText.png')` }}></Col>
        <Col md={7} className="my-auto">
            <div style={{
                with: '100%',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                padding: '10%',
            }}>
                <Form className="login-form" style={{width: '400px'}} onSubmit={handleSubmit}>
                    <h3 className="text-center mb-4">SIGN IN</h3>
                    <Form.Group controlId="formBasicEmail">
                        <Form.Label>Username</Form.Label>
                        <Form.Control type="text" placeholder="Username" />
                    </Form.Group>

                    <Form.Group controlId="formBasicPassword">
                        <Form.Label>Password</Form.Label>
                        <Form.Control type="password" placeholder="Password" />
                    </Form.Group>

                    <Form.Group controlId="formBasicCheckbox" className="mb-3">
                        <Row>
                            <Col xs={6} className="text-start">
                            <Form.Check type="checkbox" label="Remember me" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
                            </Col>
                            <Col xs={6} className="text-end">
                            <a href="/forgot-password">Forgot Password?</a>
                            </Col>
                        </Row>
                    </Form.Group>

                    {/* <BrutalismButton onClick={{handleSubmit}}>
                        Sign In
                    </BrutalismButton>               */}
                    <Button variant="primary" type="submit" className="w-100">
                    Sign In
                    </Button>

                    <div className="text-center mt-2">
                    New to Wildbook? <a href="/request-account">Request Account</a>
                    </div>
                </Form>
            </div>
          
        </Col>
      </Row>
      <Row>
        <Col className="text-center py-2">Footer</Col>
      </Row>
    </Container>
  );
}

export default LoginPage;
