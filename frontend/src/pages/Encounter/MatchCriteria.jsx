import { observer } from "mobx-react-lite";
import React, { Suspense, lazy } from "react";
import { Modal } from "react-bootstrap";
import SelectInput from "../../components/generalInputs/SelectInput";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import Select from "react-select";
import ContainerWithSpinner from "../../components/ContainerWithSpinner";

const TreeSelect = lazy(() => import("antd/es/tree-select"));

export const MatchCriteriaModal = observer(function MatchCriteriaModal({
  store = {},
  isOpen = false,
  onClose = () => {},
}) {
  const theme = React.useContext(ThemeColorContext);
  const siteSettingsLoading = Boolean(store?.siteSettingsLoading);

  return (
    <Modal
      show={isOpen}
      onHide={onClose}
      centered
      scrollable
      keyboard
      style={{ zIndex: 2000 }}
    >
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="MATCH_CRITERIA" />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <div className="match-criteria">
          <FormattedMessage id="MATCH_DESC_1" />

          <p className="mt-3 mb-1">
            <FormattedMessage id="LOCATION_ID" />
          </p>

          <ContainerWithSpinner loading={siteSettingsLoading}>
            <Suspense fallback={<div>Loading location picker...</div>}>
              <TreeSelect
                id="location-tree-select"
                treeData={store.locationIdOptions ?? []}
                value={store?.newMatch?.locationId || []}
                disabled={siteSettingsLoading}
                placeholder={
                  siteSettingsLoading
                    ? "Loading locations..."
                    : "Select locations"
                }
                notFoundContent={
                  siteSettingsLoading ? "Loading locations..." : "No locations"
                }
                treeCheckable
                treeCheckStrictly
                showCheckedStrategy="SHOW_ALL"
                treeNodeFilterProp="value"
                treeLine
                showSearch
                size="large"
                allowClear
                style={{ width: "100%" }}
                dropdownStyle={{ maxHeight: 500, zIndex: 9999 }}
                onChange={(vals, labels, extra) => {
                  if (siteSettingsLoading) return;
                  store?.newMatch?.handleStrictChange?.(vals, labels, extra);
                }}
              />
            </Suspense>
          </ContainerWithSpinner>

          <ContainerWithSpinner loading={siteSettingsLoading}>
            <SelectInput
              label="OWNER"
              options={[{ label: "My Data", value: "mydata" }]}
              value={store?.newMatch?.owner}
              onChange={(v) => store?.newMatch?.setOwner?.(v)}
              disabled={siteSettingsLoading}
            />
          </ContainerWithSpinner>

          <ContainerWithSpinner loading={siteSettingsLoading}>
            <Select
              isMulti
              options={store?.newMatch?.algorithmOptions ?? []}
              className="basic-multi-select"
              classNamePrefix="select"
              menuPlacement="auto"
              menuPortalTarget={document.body}
              styles={{ menuPortal: (base) => ({ ...base, zIndex: 9999 }) }}
              isLoading={siteSettingsLoading}
              isDisabled={siteSettingsLoading}
              placeholder={
                siteSettingsLoading
                  ? "Loading algorithms..."
                  : "Select algorithms"
              }
              loadingMessage={() => "Loading algorithms..."}
              noOptionsMessage={() =>
                siteSettingsLoading
                  ? "Loading algorithms..."
                  : "No algorithms available"
              }
              value={(store?.newMatch?.algorithmOptions ?? []).filter((o) =>
                (store?.newMatch?.algorithms ?? []).includes(o.value),
              )}
              onChange={(newValue) => {
                if (siteSettingsLoading) return;
                store?.newMatch?.setAlgorithm?.(
                  (newValue ?? []).map((o) => o.value),
                );
              }}
              closeMenuOnSelect={false}
            />
          </ContainerWithSpinner>

          <div className="d-flex justify-content-between align-items-center w-100 flex-wrap mt-3">
            <MainButton
              noArrow
              backgroundColor={theme.primaryColors.primary700}
              color="white"
              disabled={
                siteSettingsLoading ||
                (store?.newMatch?.algorithms?.length ?? 0) === 0 ||
                (store?.newMatch?.annotationIds?.length ?? 0) === 0
              }
              onClick={async () => {
                const result = await store?.newMatch?.buildNewMatchPayload?.();
                if (result?.status === 200) {
                  const url = `/iaResults.jsp?taskId=${result?.data?.taskId}`;
                  window.open(url, "_blank");
                  store?.modals?.setOpenMatchCriteriaModal?.(false);
                } else {
                  alert(
                    "There was an error creating the match. Please try again.",
                  );
                }
              }}
            >
              <FormattedMessage id="MATCH" />
            </MainButton>

            <MainButton
              noArrow
              variant="secondary"
              borderColor={theme.primaryColors.primary700}
              color={theme.primaryColors.primary700}
              shadowColor={theme.primaryColors.primary700}
              onClick={onClose}
            >
              <FormattedMessage id="CANCEL" />
            </MainButton>
          </div>
        </div>
      </Modal.Body>
    </Modal>
  );
});

export default MatchCriteriaModal;
