import React from "react";
import Card from "../components/Card";
import PassKey from "../components/svg/PassKey";

export default function UserAccessLog() {
  return (
    <div className="container">
      <div className="row">
        <div className="col-md-12">
          <h1>User Access Log</h1>
          <p>
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed nonne
            merninisti licere mihi ista probare, quae sunt a te dicta? Refert
            tamen, quo modo.
          </p>
          <div
            className="d-flex flex-row align-items-start"
            style={{
              height: "500px",
            }}
          >
            <Card
              icon={<PassKey />}
              title="User Access Log"
              content="Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed nonne merninisti licere mihi ista probare, quae sunt a te dicta? Refert tamen, quo modo."
              buttonText="Go somewhere"
            />
            <Card
              icon={<PassKey />}
              title="User Access Log"
              buttonText="Go somewhere"
            />
            <Card
              content="Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed nonne merninisti licere mihi ista probare, quae sunt a te dicta? Refert tamen, quo modo."
              buttonText="Button Title"
            />
            <Card
              title="User Access Log"
              content="Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed nonne merninisti licere mihi ista probare, quae sunt a te dicta? Refert tamen, quo modo."
            />
          </div>
        </div>
      </div>
    </div>
  );
}
