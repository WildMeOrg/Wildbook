import React, { useState, useEffect } from "react";
import { FormattedMessage } from "react-intl";
import { Form, Alert } from "react-bootstrap";
import { observer } from "mobx-react-lite";
import { TreeSelect, Tag } from "antd";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import { LocationFilterByMap } from "./LocationFilterByMap";

const customTagRender = (props) => {
  const { label } = props;
  return (
    <Tag
      style={{
        borderRadius: "8px",
        backgroundColor: "white",
        border: "none",
        color: "black",
        fontSize: "1rem",
      }}
    >
      {label}
    </Tag>
  );
};

function convertToTreeData(locationData) {
  return locationData.map((location) => ({
    title: location.name,
    value: location.id,
    geospatialInfo: location.geospatialInfo,
    children:
      location.locationID?.length > 0
        ? convertToTreeData(location.locationID)
        : [],
  }));
}

export const LocationID = observer(
  ({ store, mapCenterLat, mapCenterLon, mapZoom, locationData }) => {
    const [treeData, setTreeData] = useState([]);
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [modalShow, setModalShow] = useState(false);
    const theme = React.useContext(ThemeColorContext);
    const [showFilterByMap, setShowFilterByMap] = useState(false);
    const [expandedKeys, setExpandedKeys] = useState([]);

    const handleSearch = (inputValue) => {
      if (inputValue) {
        const keys = [];
        const searchTree = (nodes, ancestors = []) => {
          nodes.forEach((node) => {
            const currentPath = [...ancestors, node.value];
            if (node.title.toLowerCase().includes(inputValue.toLowerCase())) {
              ancestors.forEach((key) => {
                if (!keys.includes(key)) {
                  keys.push(key);
                }
              });
            }
            if (node.children) {
              searchTree(node.children, currentPath);
            }
          });
        };
        searchTree(treeData);
        setExpandedKeys(keys);
      } else {
        setExpandedKeys([]);
      }
    };

    const handleExpand = (newExpandedKeys) => {
      setExpandedKeys(newExpandedKeys);
    };

    useEffect(() => {
      if (locationData) {
        const data = convertToTreeData(locationData);
        setTreeData(data);
      }
    }, [JSON.stringify(locationData)]);

    return (
      <>
        <LocationFilterByMap
          store={store}
          modalShow={modalShow}
          setModalShow={setModalShow}
          treeData={treeData}
          mapCenterLat={mapCenterLat}
          mapCenterLon={mapCenterLon}
          mapZoom={mapZoom}
          setShowFilterByMap={setShowFilterByMap}
        />
        <Form.Group>
          <h5>
            <FormattedMessage id="PLACE_SECTION" />
            {store.placeSection.required && <span>*</span>}
          </h5>
          <p className="fs-6">
            <FormattedMessage id="LOCATION_ID_REQUIRED_WARNING" />
          </p>
          <Form.Label>
            <FormattedMessage id="LOCATION_ID" />
            {store.placeSection.required && <span>*</span>}
          </Form.Label>
          <div className="position-relative d-inline-block w-100 mb-3">
            <TreeSelect
              treeData={treeData}
              open={dropdownOpen}
              tagRender={customTagRender}
              onDropdownVisibleChange={(open) => setDropdownOpen(open)}
              value={store.placeSection.locationId}
              treeCheckStrictly
              treeNodeFilterProp="title"
              onChange={(selectedValues) => {
                store.setLocationError(selectedValues ? false : true);
                const singleSelection =
                  selectedValues.length > 0
                    ? selectedValues[selectedValues.length - 1]
                    : null;
                store.setLocationId(singleSelection?.value || null);
              }}
              treeExpandedKeys={expandedKeys}
              onSearch={handleSearch}
              onTreeExpand={handleExpand}
              showSearch
              style={{ width: "100%" }}
              placeholder={<FormattedMessage id="LOCATIONID_INSTRUCTION" />}
              allowClear
              treeCheckable={true}
              size="large"
              treeLine
              dropdownRender={(menu) => (
                <div
                  style={{
                    maxHeight: "400px",
                    padding: "10px",
                  }}
                >
                  <div
                    style={{
                      overflowY: "auto",
                      padding: "10px",
                      zIndex: 9999,
                    }}
                  >
                    {menu}
                  </div>
                  <div
                    className="d-flex justify-content-between align-items-center"
                    style={{
                      position: "sticky",
                      bottom: 0,
                      paddingLeft: "10px",
                      width: "100%",
                      height: "50px",
                    }}
                  >
                    <a
                      style={{
                        color: "blue",
                      }}
                      onClick={() => {
                        setModalShow(true);
                      }}
                    >
                      {showFilterByMap && (
                        <FormattedMessage id="FILTER_BY_MAP" />
                      )}
                    </a>
                    <div className="d-flex flex-row">
                      <MainButton
                        noArrow={true}
                        backgroundColor={theme.primaryColors.primary500}
                        color="white"
                        className="btn btn-primary"
                        onClick={() => {
                          setDropdownOpen(false);
                        }}
                      >
                        {" "}
                        <FormattedMessage id="DONE" />
                      </MainButton>
                      <MainButton
                        noArrow={true}
                        backgroundColor={"white"}
                        color={theme.primaryColors.primary500}
                        className="btn btn-primary"
                        onClick={() => {
                          setDropdownOpen(false);
                          store.setLocationId(null);
                        }}
                      >
                        {" "}
                        <FormattedMessage id="CANCEL" />
                      </MainButton>
                    </div>
                  </div>
                </div>
              )}
              dropdownStyle={{
                maxHeight: "500px",
                backgroundColor: "white",
                padding: 0,
                paddingBottom: "20px",
              }}
            />
          </div>
          {store.placeSection.error && (
            <Alert
              variant="danger"
              style={{
                marginTop: "10px",
              }}
            >
              <i
                className="bi bi-info-circle-fill"
                style={{
                  marginRight: "8px",
                  color: theme.statusColors.red600,
                }}
              ></i>
              <FormattedMessage id="LOCATION_ID_REQUIRED_WARNING" />
            </Alert>
          )}
        </Form.Group>
      </>
    );
  },
);
