
import React from "react";
import BrutalismButton from "../BrutalismButton";
import { FormattedMessage } from "react-intl";

export default function LatestActivity({
  name, 
  num,
  date, 
  text,
  latestId,
  disabled,
  onViewClick
}) {

  console.log(name, num, date, text, latestId, disabled)

    return (
        <div className="activity-item" style={{ marginBottom: '0.5em'}}>
          <h5><FormattedMessage id={name} /></h5>
          {
            num && <span>
              <FormattedMessage id='FILES_LOADED' values={{num: num}}/> |{' '}
            </span>
          }          
          <span >
            {date}
          </span>
          <BrutalismButton 
            link={latestId}
            disabled={disabled}
          >
            {text ? <FormattedMessage id="VIEW"/> : <FormattedMessage id="NONE"/>}
          </BrutalismButton>
        </div>
      );
}