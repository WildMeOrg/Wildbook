import React from "react";
import { observer } from "mobx-react-lite";
import { Form } from "react-bootstrap";
import dayjs from "dayjs";
import { DatePicker } from "antd";
import { FormattedMessage } from "react-intl";
import CardWithEditButton from "../../../components/CardWithEditButton";
import CardWithSaveAndCancelButtons from "../../../components/CardWithSaveAndCancelButtons";
import { AttributesAndValueComponent } from "../../../components/AttributesAndValueComponent";
import MetadataIcon from "../../../components/icons/MetaDataIcon";

const DetailsCard = observer(({ store }) => {
  const individual = store.individualData;
  const access = individual?.access;
  const draft = store.detailsDraft;
  const errors = store.detailsErrors;

  if (store.editDetailsCard) {
    return (
      <CardWithSaveAndCancelButtons
        icon={<MetadataIcon />}
        title="DETAILS"
        onSave={store.saveDetailsCard}
        onCancel={store.cancelDetailsEdit}
        disabled={store.savingDetails || store.siteSettingsLoading}
        content={
          <Form className="d-flex flex-column gap-3">
            <Form.Group>
              <Form.Label className="mb-1">
                <FormattedMessage id="TAXONOMY" defaultMessage="Taxonomy" />
              </Form.Label>
              <Form.Select
                value={draft?.taxonomy || ""}
                onChange={(e) =>
                  store.setDetailsField("taxonomy", e.target.value)
                }
                disabled={store.siteSettingsLoading}
                isInvalid={!!errors.taxonomy}
              >
                <option value="" disabled>
                  <FormattedMessage
                    id="SELECT_OPTION"
                    defaultMessage="Select"
                  />
                </option>
                {store.taxonomyOptions.map((taxonomy) => (
                  <option key={taxonomy.value} value={taxonomy.value}>
                    {taxonomy.label}
                  </option>
                ))}
              </Form.Select>
              <Form.Control.Feedback type="invalid">
                {errors.taxonomy}
              </Form.Control.Feedback>
            </Form.Group>

            <Form.Group>
              <Form.Label className="mb-1">
                <FormattedMessage id="SEX" defaultMessage="Sex" />
              </Form.Label>
              <Form.Select
                value={draft?.sex || ""}
                onChange={(e) => store.setDetailsField("sex", e.target.value)}
                disabled={store.siteSettingsLoading}
                isInvalid={!!errors.sex}
              >
                <option value="" disabled>
                  <FormattedMessage
                    id="SELECT_OPTION"
                    defaultMessage="Select"
                  />
                </option>
                {store.sexOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Form.Select>
              <Form.Control.Feedback type="invalid">
                {errors.sex}
              </Form.Control.Feedback>
            </Form.Group>

            <Form.Group>
              <Form.Label className="mb-1">
                <FormattedMessage
                  id="DATE_OF_BIRTH"
                  defaultMessage="Date of Birth"
                />
              </Form.Label>
              <div>
                <DatePicker
                  style={{ width: "100%" }}
                  value={draft?.dateOfBirth ? dayjs(draft.dateOfBirth) : null}
                  format="YYYY-MM-DD"
                  onChange={(date) =>
                    store.setDetailsField(
                      "dateOfBirth",
                      date ? date.format("YYYY-MM-DD") : "",
                    )
                  }
                  status={errors.dateOfBirth ? "error" : ""}
                  allowClear
                />
                {errors.dateOfBirth && (
                  <div className="invalid-feedback d-block">
                    {errors.dateOfBirth}
                  </div>
                )}
              </div>
            </Form.Group>

            <Form.Group>
              <Form.Label className="mb-1">
                <FormattedMessage
                  id="DATE_OF_DEATH"
                  defaultMessage="Date of Death"
                />
              </Form.Label>
              <div>
                <DatePicker
                  style={{ width: "100%" }}
                  value={draft?.dateOfDeath ? dayjs(draft.dateOfDeath) : null}
                  format="YYYY-MM-DD"
                  onChange={(date) =>
                    store.setDetailsField(
                      "dateOfDeath",
                      date ? date.format("YYYY-MM-DD") : "",
                    )
                  }
                  status={errors.dateOfDeath ? "error" : ""}
                  allowClear
                />
                {errors.dateOfDeath && (
                  <div className="invalid-feedback d-block">
                    {errors.dateOfDeath}
                  </div>
                )}
              </div>
            </Form.Group>

            <Form.Group>
              <Form.Label className="mb-1">
                <FormattedMessage id="STATUS" defaultMessage="Status" />
              </Form.Label>
              <Form.Select
                value={draft?.livingStatus || ""}
                onChange={(e) =>
                  store.setDetailsField("livingStatus", e.target.value)
                }
                disabled={store.siteSettingsLoading}
                isInvalid={!!errors.livingStatus}
              >
                <option value="" disabled>
                  <FormattedMessage
                    id="SELECT_OPTION"
                    defaultMessage="Select"
                  />
                </option>
                {store.livingStatusOptions.map((status) => (
                  <option key={status.value} value={status.value}>
                    {status.label}
                  </option>
                ))}
              </Form.Select>
              <Form.Control.Feedback type="invalid">
                {errors.livingStatus}
              </Form.Control.Feedback>
            </Form.Group>

            <Form.Group>
              <Form.Label className="mb-1">
                <FormattedMessage
                  id="ALTERNATE_ID"
                  defaultMessage="Alternate ID"
                />
              </Form.Label>

              <div className="d-flex flex-wrap gap-2 mb-2">
                {(draft?.alternateIds || []).map((id) => (
                  <span
                    key={id}
                    className="d-inline-flex align-items-center px-2 py-1 rounded bg-light border"
                  >
                    <span className="me-2">{id}</span>
                    <button
                      type="button"
                      className="btn-close btn-sm"
                      aria-label="Remove"
                      onClick={() => store.removeAlternateIdDraft(id)}
                      style={{ fontSize: "0.65rem" }}
                    />
                  </span>
                ))}
              </div>

              <div className="d-flex gap-2">
                <Form.Control
                  type="text"
                  placeholder="Add alternate ID"
                  value={store.detailsAltInput}
                  onChange={(e) => store.setDetailsAltInput(e.target.value)}
                  isInvalid={!!errors.alternateIds}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === ",") {
                      e.preventDefault();
                      store.addAlternateIdDraft();
                    }

                    if (
                      e.key === "Backspace" &&
                      !store.detailsAltInput &&
                      draft?.alternateIds?.length
                    ) {
                      e.preventDefault();
                      const last =
                        draft.alternateIds[draft.alternateIds.length - 1];
                      store.removeAlternateIdDraft(last);
                    }
                  }}
                />
                <button
                  type="button"
                  className="btn btn-outline-primary"
                  onClick={store.addAlternateIdDraft}
                >
                  <FormattedMessage id="ADD" defaultMessage="Add" />
                </button>
              </div>
              {errors.alternateIds && (
                <div className="invalid-feedback d-block">
                  {errors.alternateIds}
                </div>
              )}
            </Form.Group>

            <Form.Group>
              <Form.Label className="mb-1">
                <FormattedMessage
                  id="ADDITIONAL_COMMENTS"
                  defaultMessage="Additional Comments"
                />
              </Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                placeholder="Type here"
                value={draft?.additionalComments || ""}
                onChange={(e) =>
                  store.setDetailsField("additionalComments", e.target.value)
                }
                isInvalid={!!errors.additionalComments}
              />
              <Form.Control.Feedback type="invalid">
                {errors.additionalComments}
              </Form.Control.Feedback>
            </Form.Group>
          </Form>
        }
      />
    );
  }

  return (
    <CardWithEditButton
      icon={<MetadataIcon />}
      title="DETAILS"
      onClick={store.startDetailsEdit}
      showEditButton={access === "write"}
      content={
        <div>
          <AttributesAndValueComponent
            attributeId="TAXONOMY"
            value={individual?.taxonomy || "-"}
          />
          <AttributesAndValueComponent
            attributeId="SEX"
            value={individual?.sex || "-"}
          />
          <AttributesAndValueComponent
            attributeId="DATE_OF_BIRTH"
            value={individual?.dateOfBirth || "-"}
          />
          <AttributesAndValueComponent
            attributeId="DATE_OF_DEATH"
            value={individual?.dateOfDeath || "-"}
          />
          <AttributesAndValueComponent
            attributeId="STATUS"
            value={individual?.livingStatus || "Alive"}
          />
          <AttributesAndValueComponent
            attributeId="IDENTIFIABLE_SCARS"
            value={individual?.identifyingScars || "-"}
          />

          {store.alternateIds.length > 0 && (
            <div className="mb-2">
              <h6 className="mb-1">
                <FormattedMessage id="ALTERNATE_ID" />:
              </h6>
              <p className="mb-0">{store.alternateIds.join(", ")}</p>
            </div>
          )}

          {individual?.additionalComments && (
            <div className="mt-2">
              <h6 className="mb-1">
                <FormattedMessage
                  id="ADDITIONAL_COMMENTS"
                  defaultMessage="Additional Comments"
                />
              </h6>
              <p className="mb-0">{individual.additionalComments}</p>
            </div>
          )}
        </div>
      }
    />
  );
});

export default DetailsCard;
