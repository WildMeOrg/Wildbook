import React, { useContext } from 'react';
import { observer } from 'mobx-react-lite';
import LocaleContext from '../../IntlProvider';

export const MeasurementsReview = observer(({ store ={} }) => {
    const { locale } = useContext(LocaleContext) || {};
    const samplingProtocols = store.siteSettingsData?.samplingProtocol || [];

    // Sampling protocols are stored inconsistently: the classic submit.jsp saves the
    // commonConfiguration key (e.g. "samplingProtocol1") while the React editor saves the
    // configured value (e.g. "estimated"). Match either form against the site settings list
    // and show the localized label, falling back to the raw stored value when unknown.
    const localizeSamplingProtocol = (samplingProtocol) => {
        if (!samplingProtocol) return samplingProtocol;
        const match = samplingProtocols.find(
            (protocol, index) =>
                protocol.value === samplingProtocol ||
                `samplingProtocol${index}` === samplingProtocol,
        );
        if (!match) return samplingProtocol;
        return match.label?.[locale] || match.label?.en || samplingProtocol;
    };

    return <div>
        {store.showMeasurements && (
            <>
                {store.measurementTypes?.map((type, index) => { 
                    const measurement = store.encounterData?.measurements?.find(m => m.type === type);
                    if (!measurement) return <div key={index}>
                        <h6>{type}</h6>
                        <p>{`${" "}`}</p>
                    </div>; 
                    return <div key={index}>
                        <h6>{measurement.type}</h6>
                        <p>{`${measurement.value}${" "}${measurement.units} (${localizeSamplingProtocol(measurement.samplingProtocol)})`}</p>
                    </div>
                })
                }
            </>
        )}
    </div>
})
