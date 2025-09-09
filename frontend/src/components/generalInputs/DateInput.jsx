
import React from 'react';

import PropTypes from 'prop-types';

export default function DateInput({
    value,
    onChange,
    placeholder = "Select date",
    className = "",
    label = "Date Input",
    ...props
}) {
    return (
        <div className={`date-input-container ${className}`}>
            {label && <h6>{label}</h6>}
            <input
                type="date"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                placeholder={placeholder}
                className={`form-control ${className}`}
                {...props}
            />
        </div>
    );
}

DateInput.propTypes = {
    value: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired,
    placeholder: PropTypes.string,
    className: PropTypes.string,
};