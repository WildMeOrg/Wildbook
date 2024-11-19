import React, { useEffect, useState } from "react";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import { observer } from "mobx-react-lite";
import "./reportEncounter.css";
import { TreeSelect, Tag } from "antd";
import Map from "../../components/Map";

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

const filterLocationsInBounds = (treeData, bounds) => {
  if (bounds !== null) {
    const { north, south, east, west } = bounds;
    const isWithinBounds = (geospatialInfo) => {
      if (!geospatialInfo) return false;
      const { lat, long } = geospatialInfo;
      return lat <= north && lat >= south && long <= east && long >= west;
    };

    const filterTree = (nodes) => {
      return nodes
        .map((node) => {
          const filteredChildren = filterTree(node.children || []);
          const nodeInBounds =
            isWithinBounds(node.geospatialInfo) || filteredChildren.length > 0;
          if (nodeInBounds) {
            return {
              ...node,
              children: filteredChildren,
            };
          }
          return null;
        })
        .filter((node) => node !== null);
    };
    return filterTree(treeData);
  }
  return;
};

export const LocationFilterByMap = observer(
  ({
    store,
    modalShow,
    setModalShow,
    treeData,
    mapCenterLat,
    mapCenterLon,
    mapZoom,
    setShowFilterByMap,
  }) => {
    const theme = React.useContext(ThemeColorContext);
    const [mapTreeData, setMapTreeData] = useState([]);
    const [isMouseUp, setIsMouseUp] = useState(false);
    const globalBounds = {
      north: 90,
      south: -90,
      east: 180,
      west: -180,
    };
    const [bounds, setBounds] = useState(globalBounds);
    const [expandedKeys, setExpandedKeys] = useState([]);

    useEffect(() => {
      if (!bounds) setBounds(globalBounds);
    }, [bounds]);

    useEffect(() => {
      const handleMouseUp = () => {
        setIsMouseUp(true);
      };
      window.addEventListener("mouseup", handleMouseUp);
      return () => {
        window.removeEventListener("mouseup", handleMouseUp);
      };
    }, []);

    const handleSearch = (inputValue) => {
      if (inputValue) {
        const keys = [];
        const searchTree = (nodes, ancestors = []) => {
          nodes.forEach((node) => {
            const currentPath = [...ancestors, node.value];
            if (node.title.toLowerCase().includes(inputValue.toLowerCase())) {
              currentPath.forEach((key) => {
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
      if (isMouseUp && bounds) {
        setShowFilterByMap(
          filterLocationsInBounds(treeData, bounds).length > 0,
        );
        setMapTreeData(filterLocationsInBounds(treeData, bounds));
      }
    }, [JSON.stringify(treeData), bounds, isMouseUp]);

    return (
      <Modal
        dialogClassName="modal-90w"
        show={modalShow}
        size="lg"
        onHide={() => setModalShow(false)}
        keyboard
        centered
        animation
      >
        <Modal.Header
          closeButton
          style={{
            border: "none",
            paddingBottom: "0px",
          }}
        ></Modal.Header>
        <div className="d-flex flex-row pb-4 ps-4">
          <div
            className="mt-4"
            style={{
              width: "60%",
              height: "400px",
              borderRadius: "15px",
              overflow: "hidden",
            }}
          >
            {
              <Map
                bounds={bounds}
                setBounds={setBounds}
                center={{
                  lat: mapCenterLat || -1.286389,
                  lng: mapCenterLon || 36.817223,
                }}
                zoom={mapZoom || 4}
              />
            }
          </div>
          <div
            className="d-flex flex-column justify-content-between"
            style={{
              width: "40%",
              padding: "20px",
            }}
          >
            <div>
              <p>
                <FormattedMessage id="LOCATION_ID" />
              </p>
              <p>
                <FormattedMessage id="LOCATION_ID_DESCRIPTION" />
              </p>
              <TreeSelect
                key={"treeselecttwo"}
                treeData={mapTreeData}
                value={store.placeSection.locationId}
                tagRender={customTagRender}
                treeCheckStrictly
                treeNodeFilterProp="title"
                treeExpandedKeys={expandedKeys}
                onSearch={handleSearch}
                onTreeExpand={handleExpand}
                onChange={(selectedValues) => {
                  const singleSelection =
                    selectedValues.length > 0
                      ? selectedValues[selectedValues.length - 1]
                      : null;
                  store.setLocationId(singleSelection?.value || null);
                }}
                showSearch
                style={{ width: "100%" }}
                placeholder={<FormattedMessage id="LOCATIONID_INSTRUCTION" />}
                allowClear
                treeCheckable={true}
                size="large"
                treeLine
                dropdownStyle={{
                  maxHeight: "500px",
                  zIndex: 9999,
                }}
              />
            </div>
            <div className="d-flex flex-row mt-4">
              <MainButton
                noArrow={true}
                backgroundColor={theme.primaryColors.primary500}
                color="white"
                className="btn btn-primary"
                onClick={() => {
                  setModalShow(false);
                }}
              >
                {" "}
                <FormattedMessage id="DONE" />
              </MainButton>
              <MainButton
                noArrow={true}
                backgroundColor="white"
                color={theme.primaryColors.primary500}
                className="btn btn-primary"
                onClick={() => {
                  store.setLocationId(null);
                  setModalShow(false);
                }}
              >
                {" "}
                <FormattedMessage id="CANCEL" />
              </MainButton>
            </div>
          </div>
        </div>
      </Modal>
    );
  },
);
