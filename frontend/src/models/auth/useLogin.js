import { useState } from "react";
import axios from "axios";
import { get } from "lodash-es";
import { useIntl } from "react-intl";
import { useLocation } from "react-router-dom";

export default function useLogin() {
  const intl = useIntl();
  const location = useLocation();

  const errorMessage = intl.formatMessage({
    id: "LOGIN_INVALID_EMAIL_OR_PASSWORD",
  });

  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const authenticate = async (username, password) => {
    try {
      setLoading(true);
      const response = await axios.request({
        url: `/api/v3/login`,
        method: "post",
        data: {
          username,
          password,
        },
      });

      // use .startsWith("/") to prevent open redirects
      const successful = get(response, "data.success", false);
      const nextLocation = get(response, "data.redirectUrl", null) || `${new URL(process.env.PUBLIC_URL).pathname}${new URLSearchParams(location.search).get("redirect")?.startsWith("/")}`;

      const nextLocation = get(response, "data.redirectUrl", null)
        || (new URLSearchParams(location.search).get("redirect")?.startsWith("/")
          ? `${new URL(process.env.PUBLIC_URL).pathname}${new URLSearchParams(location.search).get("redirect")}${location.hash}`
          : null);

      if (successful) {
        let url = nextLocation || `${process.env.PUBLIC_URL}/home`;
        window.location.href = url;

        // Fun quirk - a reload is required if there is a hash in the URL.
        // https://stackoverflow.com/questions/10612438/javascript-reload-the-page-with-hash-value
        if (nextLocation?.hash) window.location.reload();
      } else {
        setError(errorMessage);
      }
    } catch (loginError) {
      setLoading(false);
      setError(errorMessage);
      console.error("Error logging in");
      console.error(loginError);
    }
  };

  return { authenticate, error, setError, loading };
}
