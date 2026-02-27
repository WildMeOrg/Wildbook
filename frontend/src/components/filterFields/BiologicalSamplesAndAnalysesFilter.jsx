import React, { useState, useEffect } from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import BioMeasurements from "../Form/BioMeasurements";
import { useIntl } from "react-intl";
import { observer } from "mobx-react-lite";
import ContainerWithSpinner from "../ContainerWithSpinner";

const BiologicalSamplesAndAnalysesFilter = ({ data, store }) => {
  const intl = useIntl();
  const label = <FormattedMessage id="FILTER_HAS_BIOLOGICAL_SAMPLE" />;
  const bioMeasurementOptions =
    Object.entries(data?.bioMeasurement || {}).map((item) => item[0]) || [];

  const microSatelliteMarkerLoci = data?.loci || [];

  const [checkedState, setCheckedState] = useState({});
  const [alleleLength, setAlleleLength] = React.useState(false);
  const [length, setLength] = React.useState(null);

  const haploTypeOptions =
    data?.haplotype.map((item) => {
      return {
        value: typeof item === "object" ? item.value : item,
        label: typeof item === "object" ? item.label : item,
      };
    }) || [];

  const geneticSexOptions =
    data?.geneticSex.map((item) => {
      return {
        value: typeof item === "object" ? item.value : item,
        label: typeof item === "object" ? item.label : item,
      };
    }) || [];

  const [currentValues, setCurrentValues] = useState({});

  const buildQuery_range = (data, i, value) => {
    store.addFilter(
      `microsatelliteMarkers.loci.${data}.allele${i}`,
      "filter",
      {
        range: {
          [`microsatelliteMarkers.loci.${data}.allele${i}`]: {
            gte: parseInt(value, 10) - parseInt(length),
            lte: parseInt(value, 10) + parseInt(length),
          },
        },
      },
      `microsatelliteMarkers.loci.${data}.allele${i}`,
    );
  };

  const buildQuery_match = (data, i, value) => {
    store.addFilter(
      `microsatelliteMarkers.loci.${data}.allele${i}`,
      "filter",
      {
        match: {
          [`microsatelliteMarkers.loci.${data}.allele${i}`]: value,
        },
      },
      `microsatelliteMarkers.loci.${data}.allele${i}`,
    );
  };

  useEffect(() => {
    // Iterate through each marker and re-calculate the query if necessary
    Object.keys(currentValues).forEach((data) => {
      if (checkedState[data]) {
        const value0 = currentValues[data]?.allele0;
        const value1 = currentValues[data]?.allele1;
        if (value0 !== undefined) {
          if (alleleLength && length) {
            buildQuery_range(data, 0, value0);
          } else {
            buildQuery_match(data, 0, value0);
          }
        }
        if (value1 !== undefined) {
          if (alleleLength && length) {
            buildQuery_range(data, 1, value1);
          } else {
            buildQuery_match(data, 1, value1);
          }
        }
      }
    });
  }, [alleleLength, length]);

  const handleInputChange = (data, index, value) => {
    setCurrentValues((prevState) => ({
      ...prevState,
      [data]: {
        ...prevState[data],
        [`allele${index}`]: value,
      },
    }));

    if (checkedState[data]) {
      if (value === "") {
        store.removeFilter(`microsatelliteMarkers.loci.${data}.allele${index}`);
      } else if (alleleLength && length) {
        buildQuery_range(data, index, value);
      } else {
        buildQuery_match(data, index, value);
      }
    }
  };

  useEffect(() => {
    const formFilters = store.formFilters.filter((item) =>
      item.filterId.includes("microsatelliteMarkers.loci"),
    );

    if (data?.loci) {
      if (formFilters.length > 0) {
        const formFiltersLociFields = Array.from(
          new Set(formFilters.map((item) => item.filterId.split(".")[2])),
        );
        const newCurrentValues = {};

        formFiltersLociFields.forEach((item) => {
          setCheckedState((prevState) => ({
            ...prevState,
            [item]: true,
          }));
          const formallele0 = formFilters.find(
            (filter) =>
              filter.filterId === `microsatelliteMarkers.loci.${item}.allele0`,
          );
          const formallele1 = formFilters.find(
            (filter) =>
              filter.filterId === `microsatelliteMarkers.loci.${item}.allele1`,
          );

          const isMatchFilter = formFilters.some(
            (filter) =>
              filter.filterId.includes(`microsatelliteMarkers.loci.${item}`) &&
              filter.query.match,
          );

          const isRangeFilter = formFilters.some(
            (filter) =>
              filter.filterId.includes(`microsatelliteMarkers.loci.${item}`) &&
              filter.query.range,
          );

          if (isMatchFilter) {
            let allele0 = "";
            let allele1 = "";
            setLength(0);
            setAlleleLength(false);
            if (formallele0) {
              allele0 =
                formallele0.query.match[
                  `microsatelliteMarkers.loci.${item}.allele0`
                ];
            }
            if (formallele1) {
              allele1 =
                formallele1.query.match[
                  `microsatelliteMarkers.loci.${item}.allele1`
                ];
            }

            newCurrentValues[item] = {
              allele0,
              allele1,
            };
          } else if (isRangeFilter) {
            setAlleleLength(true);
            let allele0 = "";
            let allele1 = "";
            let checkboxValue = "";

            if (formallele0) {
              const gte = parseInt(
                formallele0.query.range[
                  `microsatelliteMarkers.loci.${item}.allele0`
                ].gte,
              );
              const lte = parseInt(
                formallele0.query.range[
                  `microsatelliteMarkers.loci.${item}.allele0`
                ].lte,
              );
              allele0 = (gte + lte) / 2;
              checkboxValue = (lte - gte) / 2;
            }
            if (formallele1) {
              const gte = parseInt(
                formallele1.query.range[
                  `microsatelliteMarkers.loci.${item}.allele1`
                ].gte,
              );
              const lte = parseInt(
                formallele1.query.range[
                  `microsatelliteMarkers.loci.${item}.allele1`
                ].lte,
              );
              allele1 = (gte + lte) / 2;
            }

            setLength(checkboxValue);
            setAlleleLength(true);

            newCurrentValues[item] = {
              allele0,
              allele1,
            };
          }
        });
        setCurrentValues(newCurrentValues);
      }
    }
  }, [JSON.stringify(store.formFilters), data?.loci]);

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_BIOLOGICAL_SAMPLE" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_BIOLOGICAL_SAMPLE_DESC" />
      </Description>
      <Form>
        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={label}
          checked={
            !!store.formFilters.find(
              (filter) => filter.filterId === "biologicalSampleId",
            )
          }
          onChange={(e) => {
            if (!e.target.checked) {
              store.removeFilter(`biologicalSampleId`);
              return;
            } else {
              store.addFilter(
                `biologicalSampleId`,
                "filter",
                {
                  exists: {
                    field: "tissueSampleIds",
                  },
                },
                "Has Biological Sample",
              );
            }
          }}
        />
      </Form>
      <FormGroupText
        label="FILTER_BIOLOGICAL_SAMPLE_ID"
        noDesc
        field="tissueSampleIds"
        term="match"
        filterId={"tissueSampleIds"}
        filterKey={"Biological Sample ID"}
        store={store}
      />
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          label="FILTER_HAPLO_TYPE"
          options={haploTypeOptions || []}
          field={"haplotype"}
          filterId={"haplotype"}
          term={"terms"}
          filterKey={"Haplotype"}
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          label="FILTER_GENETIC_SEX"
          options={geneticSexOptions || []}
          field={"geneticSex"}
          term={"terms"}
          filterId={"geneticSex"}
          filterKey={"Genetic Sex"}
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <BioMeasurements
        data={bioMeasurementOptions}
        filterId={"biologicalMeasurements"}
        field={"biologicalMeasurements"}
        store={store}
      />

      <div className="d-flex flex-row justify-content-between">
        <h5>
          <FormattedMessage id="FILTER_MARKER_LOCI" />
        </h5>
        <div className="d-flex flex-row align-items-center">
          <Form.Check
            label={<FormattedMessage id="FILTER_RELAX_ALLELE_LENGTH" />}
            type="checkbox"
            id="custom-checkbox_ALLELE_LENGTH"
            checked={alleleLength}
            onChange={() => {
              setAlleleLength(!alleleLength);
            }}
          />
          <FormControl
            type="number"
            disabled={!alleleLength}
            placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
            style={{
              width: "70px",
              marginLeft: "10px",
            }}
            value={length}
            onChange={(e) => {
              setLength(e.target.value);
            }}
          />
        </div>
      </div>
      {microSatelliteMarkerLoci?.map((data) => {
        const handleCheckboxChange = (item) => {
          setCheckedState((prevState) => ({
            ...prevState,
            [item]: !prevState[item],
          }));
        };

        return (
          <FormGroup key={data} className="d-flex flex-column gap-2">
            <Form.Check
              type="checkbox"
              id="custom-checkbox"
              label={data}
              checked={checkedState[data]}
              onChange={() => {
                handleCheckboxChange(data);
                if (checkedState[data]) {
                  store.removeFilter(
                    `microsatelliteMarkers.loci.${data}.allele0`,
                  );
                  store.removeFilter(
                    `microsatelliteMarkers.loci.${data}.allele1`,
                  );
                } else {
                  if (currentValues[data]?.allele0) {
                    if (alleleLength && length) {
                      buildQuery_range(data, 0, currentValues[data]?.allele0);
                    } else {
                      buildQuery_match(data, 0, currentValues[data]?.allele0);
                    }
                  }
                  if (currentValues[data]?.allele1) {
                    if (alleleLength && length) {
                      buildQuery_range(data, 1, currentValues[data]?.allele1);
                    } else {
                      buildQuery_match(data, 1, currentValues[data]?.allele1);
                    }
                  }
                }
              }}
            />
            <div className="d-flex flex-row gap-3 ms-5">
              <div className="d-flex flex-column w-50">
                <FormLabel>
                  <FormattedMessage
                    id={"FILTER_ALLELE1"}
                    defaultMessage={"Allele1"}
                  />
                </FormLabel>
                <FormControl
                  className="mr-2"
                  type="text"
                  value={currentValues[data]?.allele0 || ""}
                  placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
                  onChange={(e) => handleInputChange(data, 0, e.target.value)}
                />
              </div>
              <div className="d-flex flex-column w-50">
                <FormLabel>
                  <FormattedMessage
                    id={"FILTER_ALLELE2"}
                    defaultMessage={"Allele2"}
                  />
                </FormLabel>
                <FormControl
                  type="text"
                  placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
                  value={currentValues[data]?.allele1 || ""}
                  onChange={(e) => handleInputChange(data, 1, e.target.value)}
                />
              </div>
            </div>
          </FormGroup>
        );
      })}
    </div>
  );
};

export default observer(BiologicalSamplesAndAnalysesFilter);
