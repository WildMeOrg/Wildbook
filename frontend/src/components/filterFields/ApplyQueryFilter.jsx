import React from "react";
import { Form, FormControl } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import Button from "react-bootstrap/Button";
import ThemeColorContext from "../../ThemeColorProvider";
import { useContext } from "react";

export default function ApplyQueryFilter() {
  const theme = useContext(ThemeColorContext);
  const [queryId, setQueryId] = React.useState("");
  return (
    <div>
      <h4>
        <FormattedMessage id="APPLY_SEARCH_ID" />
      </h4>
      <p>
        <FormattedMessage id="APPLY_SEARCH_ID_DESC" />
      </p>

      <Form
        className="d-flex flex-row w-100"
        style={{
          height: "40px",
        }}
      >
        <FormControl
          type="text"
          placeholder="Search ID"
          style={{
            borderRadius: " 5px 0 0 5px",
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && queryId) {
              window.location.href = `/encounter-search?searchQueryId=${queryId}`;
            }
          }}
          onChange={(e) => {
            setQueryId(e.target.value);
          }}
        />
        <Button
          style={{
            height: "40px",
            border: `1px solid white`,
            borderRadius: "0 5px 5px 0",
            backgroundColor: theme.primaryColors.primary700,
          }}
          onClick={() => {
            if (queryId) {
              window.location.href = `${process.env.PUBLIC_URL}/encounter-search?searchQueryId=${queryId}`;
            }
          }}
        >
          <FormattedMessage id="APPLY" />
        </Button>
      </Form>
    </div>
  );
}
