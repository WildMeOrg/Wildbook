import React from "react";
import { Button } from 'react-bootstrap';

export default function BrutalismButton({
    link, 
    onClick, 
    disabled, 
    color = '#000000',
    backgroundColor = 'rgba(255, 255, 255, 0.01)',
    borderColor = '#000000',
    type = 'button',
    className = '',
    children,
    ...rest 
}) {
    return (
        <Button 
            variant="primary" 
            href={link}
            onClick={onClick}
            disabled={disabled}
            type={type}
            className={className} 
            style={{
                boxSizing: 'border-box',                  
                display: 'flex',
                flexDirection: 'row',
                justifyContent: 'center',
                alignItems: 'center',
                padding: '4px 16px',
                gap: '8px',
                position: 'relative',
                width: 'auto',
                height: 'auto',
                background: backgroundColor, 
                border: `2px solid ${borderColor}`, 
                boxShadow: `4px 4px 0px ${borderColor}`,
                borderRadius: '4.8px',
                fontSize: '12px',
                color: color,
                fontWeight: 'bold',
                margin: '8px 8px 0 0',
                whiteSpace: 'nowrap',
            }}
            {...rest} 
        >
            {children}
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" className="bi bi-arrow-right-circle-fill" viewBox="0 0 16 16">
                <path d="M8 0a8 8 0 1 1 0 16A8 8 0 0 1 8 0M4.5 7.5a.5.5 0 0 0 0 1h5.793l-2.147 2.146a.5.5 0 0 0 .708.708l3-3a.5.5 0 0 0 0-.708l-3-3a.5.5 0 1 0-.708.708L10.293 7.5H4.5z"/>
            </svg>
        </Button>
    );
}
