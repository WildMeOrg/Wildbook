import React from 'react';

export default function Avatar({ avatar }) {
    console.log('Avatar', avatar);
    return (
        <div className="content col-1"
            style={{
                display: 'flex',
                justifyContent: 'space-around',
                alignItems: 'center'

            }}
        >
            <img src={avatar} alt="img"
                style={{
                    width: '40px',
                    height: '40px',
                    borderRadius: '50%'
                }}
            />
        </div>
    );
}