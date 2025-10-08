import React, { useContext } from 'react';
import { observer } from 'mobx-react-lite';
import SelectInput from '../../components/generalInputs/SelectInput';
import TextInput from '../../components/generalInputs/TextInput';
import { Divider } from '../../components/Divider';
import LocaleContext from '../../IntlProvider';

export const MeasurementsEdit = observer(({ store }) => {

    const { locale } = useContext(LocaleContext);
    const samplingProtocols = store.siteSettingsData?.samplingProtocol || [];
    const samplingProtocolOptions = samplingProtocols.map(protocol => ({
        label: protocol.label[locale] || protocol.label['en'],
        value: protocol.value
    }));
    return <div>
        {store.showMeasurements && store.measurementTypes && store.measurementTypes.length > 0 && (
            <>
                {store.measurementTypes.map((type, index) => {
                    const unitLabel = store.measurementUnits?.[index] || 'Unit';
                    const cur = store.getMeasurement(type);
                    return (
                        <div key={type}>
                            <h6>{type}</h6>
                            <TextInput
                                label={unitLabel}
                                value={cur.value ?? ''}
                                onChange={(value) => {
                                    store.errors.setFieldError("measurement", type, null);
                                    if (value === '') {
                                        store.errors.setFieldError("measurement", type, "Value cannot be empty");
                                    }
                                    store.setMeasurementValue(type, value);
                                }}
                            />
                            <SelectInput
                                label="samplingProtocol"
                                value={cur.samplingProtocol ?? ''}
                                options={samplingProtocolOptions}
                                onChange={(samplingProtocol) => {
                                    store.errors.setFieldError("measurement", type, null);
                                    if (!samplingProtocol || samplingProtocol === '') {
                                        store.errors.setFieldError("measurement", type, "Value cannot be empty");
                                    }
                                    store.setMeasurementSamplingProtocol(type, samplingProtocol);
                                }}
                            />
                            <Divider />
                            {store.errors.getFieldError("measurement", type) && (
                                <div className="invalid-feedback d-block">
                                    {store.errors.getFieldError("measurement", type) || ""}
                                </div>
                            )}
                        </div>
                    );
                })}
            </>
        )}
    </div>
})
