import Select from 'react-select';

const colourStyles = {
    option: (styles) => ({
        ...styles,
        color: 'black', 
    }),
    control: (styles) => ({ ...styles, backgroundColor: 'white' }),
    singleValue: (styles) => ({ ...styles, color: 'black' }), 
};

export default function MultiSelect({ options, onChange }) {
    return (
        <Select
            isMulti
            options={options}
            className="basic-multi-select"
            classNamePrefix="select"
            styles={colourStyles}
            onChange={onChange}
        />
    );
}