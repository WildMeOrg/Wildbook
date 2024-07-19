import React, { useState, useContext, useEffect } from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import { FormLabel, FormGroup, FormControl } from "react-bootstrap";

export default function DateFilter({
    onChange,
    data,
}) {    
    const [startDate, setStartDate] = useState("");
    const [endDate, setEndDate] = useState("");
    const [submissionStartDate, setSubmissionStartDate] = useState('');
    const [submissionEndDate, setSubmissionEndDate] = useState('');
    const verbatimeventdateOptions = data?.verbatimEventDate?.map(data => {
        return {
            value: data,
            label: data
        }   
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
                    date: {}
                }
            };

            if (startDate) {
                query.range.date.gte = startDate + "T00:00:00Z";
            }

            if (endDate) {
                query.range.date.lte = endDate + "T23:59:59Z";
            }
            onChange({
                filterId: "date",
                clause: "filter",
                query: query
            }
            )
        }

    }

    const updateQuery2 = () => {
        if (submissionStartDate || submissionEndDate) {
            const query = {
                range: {
                    dateSubmitted: {}
                }
            };

            if (submissionStartDate) {
                query.range.dateSubmitted.gte = submissionStartDate + "T00:00:00Z";
            }

            if (submissionEndDate) {
                query.range.dateSubmitted.lte = submissionEndDate + "T23:59:59Z";
            }
            onChange({
                filterId: "dateSubmitted",
                clause: "filter",
                query: query
            }
            )
        }
    }

    return (
        <div >
            <h3><FormattedMessage id="DATE" /></h3>
            <Description>
                <FormattedMessage id="DATE_DESC" />
            </Description>
            <>
                <FormLabel><FormattedMessage id="SIGHTING_DATES" /></FormLabel>
                <div className="d-flex flex-row w-100 mb-2">
                    <FormGroup className="w-50" style={{ marginRight: '10px' }}>
                        <p>
                            <FormattedMessage id="FROM" defaultMessage="From" />
                        </p>
                        <FormControl type="date" value={startDate} onChange={(e) => {
                            setStartDate(e.target.value);
                            updateQuery1();
                        }} />
                    </FormGroup>
                    <FormGroup className="w-50">
                        <p>
                            <FormattedMessage id="TO" defaultMessage="To" />
                        </p>
                        <FormControl type="date" value={endDate} onChange={(e) => {
                            setEndDate(e.target.value);
                            updateQuery1();
                        }} />
                    </FormGroup>
                </div>
            </>

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_VERBATIM_EVENT_DATE"
                options={verbatimeventdateOptions}
                onChange={onChange}
                term="terms"
                field="verbatimEventDate"
            />

            <><p><FormLabel class="mt-3"><FormattedMessage id="ENCOUNTER_SUBMISSION_DATES" /></FormLabel></p>
                
                <div className="d-flex flex-row w-100 mb-2">
                
                    <FormGroup className="w-50" style={{ marginRight: '10px' }}>
                        <p>
                            <FormattedMessage id="FROM" defaultMessage="From" />
                        </p>
                        <FormControl type="date" value={submissionStartDate} onChange={(e) => {
                            setSubmissionStartDate(e.target.value);
                        }} />
                    </FormGroup>
                    <FormGroup className="w-50">
                        <p>
                            <FormattedMessage id="TO" defaultMessage="To" />
                        </p>
                        <FormControl type="date" value={submissionEndDate} onChange={(e) => {
                            setSubmissionEndDate(e.target.value);
                        }} />
                    </FormGroup>
                </div>
            </>

        </div>
    );
}

// import React, { useState, useEffect } from "react";
// import { FormattedMessage } from "react-intl";
// import Description from "../Form/Description";
// import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
// import { FormLabel, FormGroup, FormControl } from "react-bootstrap";

// export default function DateFilter({ onChange, data }) {
//     const loadInitialValue = () => {
//         const savedData = localStorage.getItem("formData");
//         return savedData ? JSON.parse(savedData) : {
//             startDate: "",
//             endDate: "",
//             submissionStartDate: "",
//             submissionEndDate: "",
//             verbatimEventDates: []
//         };
//     };

//     const [formData, setFormData] = useState(loadInitialValue());

//     const handleInputChange = (field, value) => {
//         const newFormData = { ...formData, [field]: value };
//         setFormData(newFormData);
//         localStorage.setItem("formData", JSON.stringify(newFormData));
//     };

//     const verbatimEventDateOptions = data?.verbatimeventdate?.map(data => ({
//         value: data,
//         label: data
//     })) || [];

//     useEffect(() => {
//         updateQuery("sightingDate", formData.startDate, formData.endDate, "date");
//         updateQuery("submissionDate", formData.submissionStartDate, formData.submissionEndDate, "dateSubmitted");
//     }, [formData.startDate, formData.endDate, formData.submissionStartDate, formData.submissionEndDate]);

//     const updateQuery = (dateType, start, end, filterId) => {
//         if (start || end) {
//             const query = { range: {} };
//             query.range[dateType] = {};

//             if (start) {
//                 query.range[dateType].gte = start + "T00:00:00Z";
//             }

//             if (end) {
//                 query.range[dateType].lte = end + "T23:59:59Z";
//             }

//             onChange({
//                 filterId: filterId,
//                 clause: "filter",
//                 query: query
//             });
//         }
//     };

//     return (
//         <div>
//             <h3><FormattedMessage id="DATE" /></h3>
//             <Description>
//                 <FormattedMessage id="DATE_DESC" />
//             </Description>
//             <>
//                 <FormLabel><FormattedMessage id="SIGHTING_DATES" /></FormLabel>
//                 <div className="d-flex flex-row w-100 mb-2">
//                     <FormGroup className="w-50" style={{ marginRight: '10px' }}>
//                         <p>
//                             <FormattedMessage id="FROM" defaultMessage="From" />
//                         </p>
//                         <FormControl type="date" value={formData.startDate} onChange={(e) => handleInputChange("startDate", e.target.value)} />
//                     </FormGroup>
//                     <FormGroup className="w-50">
//                         <p>
//                             <FormattedMessage id="TO" defaultMessage="To" />
//                         </p>
//                         <FormControl type="date" value={formData.endDate} onChange={(e) => handleInputChange("endDate", e.target.value)} />
//                     </FormGroup>
//                 </div>
//             </>

//             <FormGroupMultiSelect
//                 isMulti={true}
//                 label="FILTER_VERBATIM_EVENT_DATE"
//                 options={verbatimEventDateOptions}
//                 onChange={(e) => handleInputChange("verbatimEventDates", e)} // Adjust as necessary for multi-select handling
//                 term="terms"
//                 field="verbatimEventDate"
//             />

//             <>
//                 <p><FormLabel className="mt-3"><FormattedMessage id="ENCOUNTER_SUBMISSION_DATES" /></FormLabel></p>
//                 <div className="d-flex flex-row w-100 mb-2">
//                     <FormGroup className="w-50" style={{ marginRight: '10px' }}>
//                         <p>
//                             <FormattedMessage id="FROM" defaultMessage="From" />
//                         </p>
//                         <FormControl type="date" value={formData.submissionStartDate} onChange={(e) => handleInputChange("submissionStartDate", e.target.value)} />
//                     </FormGroup>
//                     <FormGroup className="w-50">
//                         <p>
//                             <FormattedMessage id="TO" defaultMessage="To" />
//                         </p>
//                         <FormControl type="date" value={formData.submissionEndDate} onChange={(e) => handleInputChange("submissionEndDate", e.target.value)} />
//                     </FormGroup>
//                 </div>
//             </>
//         </div>
//     );
// }
