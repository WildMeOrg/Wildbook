import Select from 'react-select';

const colourStyles = {
    option: (styles) => ({
        ...styles,
        color: 'black', 
    }),
    control: (styles) => ({ ...styles, backgroundColor: 'white' }),
    singleValue: (styles) => ({ ...styles, color: 'black' }), 
};

export default function MultiSelect({ isMulti, options, onChange, field, term }) {
    return (
        <Select
            isMulti = {isMulti}
            options={options}
            className="basic-multi-select"
            classNamePrefix="select"
            styles={colourStyles}
            
            onChange={(e) =>
                onChange({
                    filterId: field,
                    clause: "filter",
                    query:{
                        [term]: {
                            [field]: isMulti? e.map(item=> item.value) : e.value
                        }
                    }                    
                })
            }
        />
    );
}