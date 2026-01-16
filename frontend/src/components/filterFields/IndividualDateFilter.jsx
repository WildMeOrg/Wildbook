import React from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import { FormGroup } from "react-bootstrap";
import { observer } from "mobx-react-lite";
import { DatePicker } from "antd";
import dayjs from "dayjs";

const FMT = "YYYY-MM-DD";
const toDayjs = (s) => (s ? dayjs(s) : null);

const IndividualDateFilter = observer(({ store }) => {
  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_DATE" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_DATE_DESC" />
      </Description>
      <>
        <FormGroup className="w-100" style={{ marginRight: "10px" }}>
          <p>
            <FormattedMessage id="FILTER_BIRTH" defaultMessage="Birth" />
          </p>
          <DatePicker
            className="w-100"
            value={toDayjs(
              store.formFilters
                .find((filter) => filter.filterId === "individualTimeOfBirth")
                ?.query?.range?.individualTimeOfBirth?.gte?.split("T")[0] || "",
            )}
            format={FMT}
            placeholder={FMT}
            allowClear
            onChange={(d) => {
              const iso = d ? d.format(FMT) : "";
              if (iso) {
                store.addFilter(
                  "individualTimeOfBirth",
                  "filter",
                  {
                    range: {
                      individualTimeOfBirth: {
                        gte: `${iso}T00:00:00.000Z`,
                        lte: `${iso}T23:59:59.000Z`,
                      },
                    },
                  },
                  "Birth Date",
                );
              } else {
                store.removeFilter("individualTimeOfBirth");
              }
            }}
            getPopupContainer={(trigger) => trigger.parentElement}
          />
        </FormGroup>
        <FormGroup className="w-100" style={{ marginRight: "10px" }}>
          <p>
            <FormattedMessage id="FILTER_DEATH" defaultMessage="Death" />
          </p>

          <DatePicker
            className="w-100"
            value={toDayjs(
              store.formFilters
                .find((filter) => filter.filterId === "individualTimeOfDeath")
                ?.query?.range?.individualTimeOfDeath?.gte?.split("T")[0] || "",
            )}
            format={FMT}
            placeholder={FMT}
            allowClear
            onChange={(d) => {
              const iso = d ? d.format(FMT) : "";
              if (iso) {
                store.addFilter(
                  "individualTimeOfDeath",
                  "filter",
                  {
                    range: {
                      individualTimeOfDeath: {
                        gte: `${iso}T00:00:00.000Z`,
                        lte: `${iso}T23:59:59.000Z`,
                      },
                    },
                  },
                  "Death Date",
                );
              } else {
                store.removeFilter("individualTimeOfDeath");
              }
            }}
            getPopupContainer={(trigger) => trigger.parentElement}
          />
        </FormGroup>
      </>
    </div>
  );
});

export default IndividualDateFilter;
