import Select from 'react-select';
import { useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useIntl } from 'react-intl';

const colourStyles = {
    option: (styles) => ({
        ...styles,
        color: 'black',
    }),
    control: (styles) => ({ ...styles, backgroundColor: 'white' }),
    singleValue: (styles) => ({ ...styles, color: 'black' }),
    menuPortal: base => ({ ...base, zIndex: 9999 }),
    // menu: base => ({ ...base, maxHeight: '200px' }),
    control: base => ({ ...base, zIndex: 1 }),
};

export default function MultiSelect({ isMulti, options, onChange, field, filterKey, term, setFormFilters, formFilters }) {
   
    const location = useLocation();
    const [selectedOptions, setSelectedOptions] = useState([]);
    const navigate = useNavigate();
    const intl = useIntl();

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        if (field === "assignedUsername") {
            const fieldValue = params.get("username");
            if (fieldValue) {
                const selectedItems = options.filter(option => fieldValue === option.label);
                setSelectedOptions(selectedItems);
            }
        } else if (field === "state") {
            const fieldValue = params.get("state");
            if (fieldValue) {
                const selectedItems = options.filter(option => fieldValue === option.label);
                setSelectedOptions(selectedItems);
            }
        }

    }, [location.search, field, options, isMulti]);

    return (
        <Select
            isMulti={isMulti}
            options={options}
            className="basic-multi-select"
            classNamePrefix="select"
            styles={colourStyles}
            menuPlacement="auto"
            menuPortalTarget={document.body}
            placeholder={intl.formatMessage({ id: "SELECT_ONE_OR_MORE" })}            
            value={selectedOptions}
            onChange={(e) => {
                const params = new URLSearchParams(location.search);
                
                if(field === "assignedUsername") {
                    params.delete("username");
                    onChange(null, "assignedUsername");
                    // setFormFilters(formFilters.filter(filter => filter.filterId !== "assignedUsername"));
                    navigate(`${location.pathname}?${params.toString()}`, { replace: true });
                }else if(field === "state") {
                    params.delete("state");              
                    // setFormFilters(formFilters.filter(filter => filter.filterId !== "state"));
                    onChange(null, "state");
                    navigate(`${location.pathname}?${params.toString()}`, { replace: true });
                }
                
                setSelectedOptions(e || []);

                if (e?.value || e.length > 0) {
                    onChange({
                        filterId: field,
                        clause: "filter",
                        filterKey: filterKey,
                        query: {
                            [term]: {
                                [field]: isMulti ? e.map(item => item.value) : e.value
                            }
                        }
                    })
                } else {
                    onChange(null, field);
                }
            }
            }
        />
    );
}