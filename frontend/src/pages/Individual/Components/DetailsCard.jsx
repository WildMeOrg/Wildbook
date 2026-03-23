import React from "react";
import { observer } from "mobx-react-lite";
import CardWithEditButton from "../../../components/CardWithEditButton";
import { AttributesAndValueComponent } from "../../../components/AttributesAndValueComponent";
import { FormattedMessage } from "react-intl";

const DetailsCard = observer(({ store, onEdit }) => {
  const individual = store.individualData;

  return (
    <CardWithEditButton
      title="DETAILS"
      onClick={onEdit}
      showEditButton={false}
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
        </div>
      }
    />
  );
});

export default DetailsCard;
