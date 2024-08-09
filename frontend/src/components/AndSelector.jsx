import Select from 'react-select';
import { useEffect, useState } from 'react';
import { useRef } from 'react';

const colourStyles = {
    option: (styles) => ({
        ...styles,
        color: 'black',
    }),
    control: (styles) => ({ ...styles, backgroundColor: 'white' }),
    singleValue: (styles) => ({ ...styles, color: 'black' }),
    menuPortal: base => ({ ...base, zIndex: 9999 }),
    control: base => ({ ...base, zIndex: 1 }),
};

export default function MultiSelect({ isMulti, options, onChange, field, filterKey, term, }) {

    const [selectedOptions, setSelectedOptions] = useState([]);
    const selectedOptionsRef = useRef(selectedOptions);

    useEffect(() => {       
        onChange(null, field);
        return () => {
            options.forEach(option => {
                console.log(option);
                onChange(null, `${field}.${option.value}`);
            });
        };
    }, []);

    const handleChange = (selected) => {

        const addedOptions = selected.filter(option => !selectedOptions.includes(option));
        const removedOptions = selectedOptions.filter(option => !selected.includes(option));

        setSelectedOptions(selected || []);
        selectedOptionsRef.current = selected || [];

        if (addedOptions.length > 0) {
            addedOptions.forEach(option => {
                onChange({
                    filterId: `${field}.${option.value}`,
                    clause: "filter",
                    filterKey: filterKey,
                    query: {
                        "term": {
                            [field]: option.value
                        }
                    }
                });
            })
        }

        if (removedOptions.length > 0) {
            removedOptions.forEach(option => {
                onChange(null, `${field}.${option.value}`);
            });
        }
    }

    return (
        <Select
            isMulti={isMulti}
            options={options}
            className="basic-multi-select"
            classNamePrefix="select"
            styles={colourStyles}
            menuPlacement="auto"
            menuPortalTarget={document.body}
            value={selectedOptions}
            onChange={handleChange}
        />
    );
}