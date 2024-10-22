import React, { useState, useContext, useEffect } from "react";
import { FormattedMessage } from "react-intl";
import { Modal, Form, Button, InputGroup, Alert } from "react-bootstrap";
import BrutalismButton from "../../components/BrutalismButton";
import { observer, useLocalObservable } from "mobx-react-lite";
import { ReportEncounterStore } from "./ReportEncounterStore";
import { useIntl } from "react-intl";
import useLogin from "../../models/auth/useLogin";


export const SignInModal = observer(({
    showModal,
    setShowModal,
}) => {

    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const intl = useIntl();
    const { authenticate, error, setError, loading } = useLogin();
    const actionDisabled = loading;
    const [show, setShow] = useState(false);
    // const theme = useContext(ThemeColorContext);

    useEffect(() => {
        if (error) {
            setShow(true);
        }
    }, [error]);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError(null);
        setShow(false);
        authenticate(username, password, false);
    };

    const togglePasswordVisibility = () => {
        setShowPassword(!showPassword);
    };
    const store = useLocalObservable(() => new ReportEncounterStore());

    return <Modal show={showModal} centered onHide={() => {
        setShowModal(false);
        }}>
        <Modal.Header closeButton style={{ borderBottom: "none" }}>
            <Modal.Title>
                <FormattedMessage id="LOGIN" />
            </Modal.Title>
        </Modal.Header>
        <Modal.Body style={{ borderTop: "none", padding: "10px" }}>
            <Form
                className="login-form"
                style={{ width: "100%", maxWidth: "400px" }}
                onSubmit={handleSubmit}
            >

                <Form.Group controlId="formBasicEmail">
                    <Form.Label>
                        {intl.formatMessage({
                            id: "LOGIN_USERNAME",
                        })}
                    </Form.Label>
                    <Form.Control
                        type="text"
                        placeholder="Username"
                        onChange={(e) => {
                            setUsername(e.target.value);
                            setError(null);
                        }}
                    />
                </Form.Group>

                <Form.Group controlId="formBasicPassword">
                    <Form.Label>
                        {intl.formatMessage({
                            id: "LOGIN_PASSWORD",
                        })}
                    </Form.Label>
                    <InputGroup>
                        <Form.Control
                            autoComplete="current-password"
                            type={showPassword ? "text" : "password"}
                            placeholder="Password"
                            value={password}
                            onChange={(e) => {
                                setPassword(e.target.value);
                            }}
                        />
                        <InputGroup.Text
                            onClick={togglePasswordVisibility}
                            style={{ cursor: "pointer" }}
                        >
                            <i
                                className={
                                    showPassword ? "bi bi-eye-fill" : "bi bi-eye-slash-fill"
                                }
                            ></i>
                        </InputGroup.Text>
                    </InputGroup>
                </Form.Group>
                <BrutalismButton
                    type="submit"
                    className = "mt-4"
                    onClick={handleSubmit}
                    // color={theme.primaryColors.primary500}
                    // borderColor={theme.primaryColors.primary500}
                    disabled={actionDisabled}
                >
                    {intl.formatMessage({
                        id: "LOGIN_SIGN_IN",
                    })}

                    {loading && (
                        <div
                            className="spinner-border spinner-border-sm ms-1"
                            role="status"
                        >
                            <span className="visually-hidden"><FormattedMessage id="LOADING" /></span>
                        </div>
                    )}
                </BrutalismButton>

                {show && error && (
                    <Alert
                        variant="danger"
                        onClose={() => setShow(false)}
                        dismissible
                        style={{ marginTop: "20px" }}
                    >
                        <i className="bi bi-exclamation-circle"></i> {error}
                    </Alert>
                )}

                <div className="text-center mt-3 d-flex ">
                    <span>
                        <a href="/resetPassword.jsp">
                            {intl.formatMessage({
                                id: "LOGIN_FORGOT_PASSWORD",
                            })}
                        </a>
                    </span>
                    <span style={{ marginLeft: 8, color: "blue" }}>|</span>
                    <span
                        style={{
                            marginLeft: "8px",
                        }}
                    >
                        <a href="https://us7.list-manage.com/contact-form?u=c5af097df0ca8712f52ea1768&form_id=335cfeba915bbb2a6058d6ba705598ce">
                            {intl.formatMessage({
                                id: "LOGIN_REQUEST_ACCOUNT",
                            })}
                        </a>
                    </span>
                </div>
            </Form>
        </Modal.Body>
        <Modal.Footer style={{ borderTop: "none" }}>
            {/* <Button
                variant="secondary"
                onClick={() => store.setSignInModalShow(false)}
            >
                Login
            </Button> */}

        </Modal.Footer>
    </Modal>
});

export default SignInModal;