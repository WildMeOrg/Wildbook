import React from 'react';
import { Container, Row, Col, Form, Button } from 'react-bootstrap';
import { useState } from 'react';
import BrutalismButton from '../components/BrutalismButton';
import Cookies from 'js-cookie';
import { Navigate, useNavigate } from 'react-router-dom';
import { useIntl } from 'react-intl';
import useLogin from '../models/auth/useLogin';


function LoginPage() {
  
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const intl = useIntl();  
  const [rememberMe, setRememberMe] = useState(false);
  const { authenticate, error, setError, loading } = useLogin();
  const actionDisabled = loading || username === '' || password === '';

  const handleSubmit = async (event) => {
      event.preventDefault();
      console.log('Form submitted');
  
      authenticate(username, password);
        // if (rememberMe) {
        //   Cookies.set('rememberMe', 'true', { expires: 1/3 });
        // } else {
        //   Cookies.set('rememberMe', 'true');
        // }  
        
      }

  return (
    <Container fluid>
      <Row className="vh-100">
        <Col 
          md={5} 
          className="d-none d-md-block bg-image p-0" 
          >
            <img 
              src="/react/forestWithText.png" 
              alt=""
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover',              
              }} />
        </Col>

        <Col md={7} className="my-auto">
            <div style={{
                with: '100%',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
            }}>
                <Form className="login-form" style={{width: '400px'}} onSubmit={handleSubmit}>
                    <h3 className="text-center mb-4">SIGN IN</h3>
                    <Form.Group controlId="formBasicEmail">
                        <Form.Label>{
                          intl.formatMessage({
                            id: 'USERNAME',
                          })
                          }</Form.Label>
                        <Form.Control type="text" placeholder="Username" onChange={e => {
                          setUsername(e.target.value);
                          setError(null);
                        }} />
                    </Form.Group>

                    <Form.Group controlId="formBasicPassword">
                        <Form.Label>{
                          intl.formatMessage({
                            id: 'PASSWORD',
                          })
                          }</Form.Label>
                        <Form.Control type="password" placeholder="Password" onChange={e => {
                          setPassword(e.target.value);
                          setError(null);
                                                  }}/>
                    </Form.Group>

                    <Form.Group controlId="formBasicCheckbox" className="mb-3 mt-3">
                        <Row>
                            <Col xs={6} className="text-start">
                            <Form.Check 
                              type="checkbox" 
                              label={
                                intl.formatMessage({
                                  id: 'REMEMBER_ME',
                                })
                                } 
                              checked={rememberMe} 
                              onChange={(e) => setRememberMe(e.target.checked)} 
                          />
                            </Col>
                            <Col xs={6} className="text-end">
                            <a href="/forgot-password">{
                            intl.formatMessage({
                              id: 'FORGOT_PASSWORD',
                            })
                          }</a>
                            </Col>
                        </Row>
                    </Form.Group>

                    <BrutalismButton onClick={handleSubmit} disabled={actionDisabled}>
                      {
                        intl.formatMessage({
                          id: 'SIGN_IN',
                        })
                      }
                    </BrutalismButton>     

                    {error && (
                      // <CustomAlert severity="error">{error}</CustomAlert>
                      <div>
                        {error}
                      </div>
                    )}         

                    <div className="text-center mt-3 d-flex justify-content-between">
                      {
                          intl.formatMessage({
                            id: 'NEW_TO_WILDBOOK',
                          })
                          }
                           <a href="/request-account">{
                              intl.formatMessage({
                                id: 'REQUEST_ACCOUNT',
                              })}
                           </a>
                    </div>
                </Form>
            </div>
          
        </Col>
      </Row>
    </Container>
  );
}

export default LoginPage;
