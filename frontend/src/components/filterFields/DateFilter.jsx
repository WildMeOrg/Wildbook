import React, { useState, useEffect } from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import { FormLabel, FormGroup } from "react-bootstrap";
import { observer } from "mobx-react-lite";
import { DatePicker } from "antd";
import dayjs from "dayjs";
import ContainerWithSpinner from "../ContainerWithSpinner";

const FMT = "YYYY-MM-DD";
const toDayjs = (s) => (s ? dayjs(s) : null);

const DateFilter = observer(({ data, store }) => {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [submissionStartDate, setSubmissionStartDate] = useState("");
  const [submissionEndDate, setSubmissionEndDate] = useState("");

  const verbatimeventdateOptions =
    data?.verbatimEventDate?.map((data) => {
      return {
        value: data,
        label: data,
      };
    }) || [];

  useEffect(() => {
    updateQuery1("startDate", startDate);
  }, [startDate]);
  useEffect(() => {
    updateQuery1("endDate", endDate);
  }, [endDate]);

  useEffect(() => {
    updateQuery2("submissionStartDate", submissionStartDate);
  }, [submissionStartDate]);

  useEffect(() => {
    updateQuery2("submissionEndDate", submissionEndDate);
  }, [submissionEndDate]);

  const updateQuery1 = () => {
    if (startDate || endDate) {
      const query = {
        range: {
          date: {},
        },
      };

      if (startDate) {
        query.range.date.gte = startDate + "T00:00:00Z";
      }

      if (endDate) {
        query.range.date.lte = endDate + "T23:59:59Z";
      }

      store.addFilter("date", "filter", query, "Sighting Date");
    } else {
      store.removeFilter("date");
    }
  };

  const updateQuery2 = () => {
    if (submissionStartDate || submissionEndDate) {
      const query = {
        range: {
          dateSubmitted: {},
        },
      };

      if (submissionStartDate) {
        query.range.dateSubmitted.gte = submissionStartDate + "T00:00:00Z";
      }

      if (submissionEndDate) {
        query.range.dateSubmitted.lte = submissionEndDate + "T23:59:59Z";
      }

      store.addFilter("dateSubmitted", "filter", query, "Date Submitted");
    } else {
      store.removeFilter("dateSubmitted");
    }
  };

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_DATE" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_DATE_DESC" />
      </Description>
      <>
        <FormLabel>
          <FormattedMessage id="FILTER_ENCOUNTER_DATE" />
        </FormLabel>
        <div className="d-flex flex-row w-100 mb-2">
          <FormGroup className="w-50" style={{ marginRight: "10px" }}>
            <p>
              <FormattedMessage id="FILTER_FROM" defaultMessage="From" />
            </p>
            <DatePicker
              className="w-100"
              value={toDayjs(
                store.formFilters
                  .find((f) => f.filterId === "date")
                  ?.query?.range?.date?.gte?.split("T")[0] || startDate,
              )}
              format={FMT}
              placeholder={FMT}
              allowClear
              onChange={(d) => {
                const iso = d ? d.format(FMT) : "";
                setStartDate(iso);
                updateQuery1();
              }}
              getPopupContainer={(trigger) => trigger.parentElement}
            />
          </FormGroup>

          <FormGroup className="w-50">
            <p>
              <FormattedMessage id="FILTER_TO" defaultMessage="To" />
            </p>
            <DatePicker
              className="w-100"
              value={toDayjs(
                store.formFilters
                  .find((f) => f.filterId === "date")
                  ?.query?.range?.date?.lte?.split("T")[0] || endDate,
              )}
              format={FMT}
              placeholder={FMT}
              allowClear
              onChange={(d) => {
                const iso = d ? d.format(FMT) : "";
                setEndDate(iso);
                updateQuery1();
              }}
              getPopupContainer={(trigger) => trigger.parentElement}
            />
          </FormGroup>
        </div>
      </>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          noDesc
          label="FILTER_VERBATIM_EVENT_DATE"
          options={verbatimeventdateOptions}
          term="terms"
          field="verbatimEventDate"
          filterKey="Verbatim Event Date"
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <>
        <FormLabel className="mt-3">
          <FormattedMessage id="FILTER_ENCOUNTER_SUBMISSION_DATE" />
        </FormLabel>
        <div className="d-flex flex-row w-100 mb-2">
          <FormGroup className="w-50" style={{ marginRight: "10px" }}>
            <p>
              <FormattedMessage id="FILTER_FROM" defaultMessage="From" />
            </p>
            <DatePicker
              className="w-100"
              value={toDayjs(
                store.formFilters
                  .find((filter) => filter.filterId === "dateSubmitted")
                  ?.query?.range?.dateSubmitted?.gte?.split("T")[0] ||
                  submissionStartDate,
              )}
              format={FMT}
              placeholder={FMT}
              allowClear
              onChange={(d) => {
                const iso = d ? d.format(FMT) : "";
                updateQuery2();
                setSubmissionStartDate(iso);
              }}
              getPopupContainer={(trigger) => trigger.parentElement}
            />
          </FormGroup>
          <FormGroup className="w-50">
            <p>
              <FormattedMessage id="FILTER_TO" defaultMessage="To" />
            </p>
            <DatePicker
              className="w-100"
              value={toDayjs(
                store.formFilters
                  .find((filter) => filter.filterId === "dateSubmitted")
                  ?.query?.range?.dateSubmitted?.lte?.split("T")[0] ||
                  submissionEndDate,
              )}
              format={FMT}
              placeholder={FMT}
              allowClear
              onChange={(d) => {
                const iso = d ? d.format(FMT) : "";
                updateQuery2();
                setSubmissionEndDate(iso);
              }}
              getPopupContainer={(trigger) => trigger.parentElement}
            />
          </FormGroup>
        </div>
      </>
    </div>
  );
});

export default DateFilter;
