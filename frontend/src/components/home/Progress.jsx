import React from "react";

export default function Progress({ 
    name, 
    encounters, 
    progress, 
    children,
    href,
    style,
    disabled = false,
    noUnderline = false,
    external = false,
    newTab = false,
    onClick,
    ...rest }) {

        console.log(noUnderline, 'noUnderline');
    const styles = {
        color: disabled ? 'grey' : 'unset',
        textDecoration: noUnderline ? 'none' : 'underline',
        cursor: disabled ? 'default' : 'pointer',
        ...style,
      };

    return (
        <div style={{
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            width: '100%',
            marginBottom: '10px',
        }}
        >
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'flex-start',
                alignItems: 'flex-start',
                width: '70%',
            }}>
                
                <a 
                    href={href}
                    target={newTab ? "_blank" : "_self"} 
                    style={{
                        textDecoration: noUnderline ? 'none' : 'underline', 
                        color: styles.color, 
                        cursor: styles.cursor, 
                    }}
                >
                    <h6>{name}</h6>
                    <span>{encounters} encounters</span>
                </a>
            </div>
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'flex-start',
                alignItems: 'flex-start',
                width: '70%',
            }}>
                <div style={{
                    width: '100%',
                    height: '15px',
                    backgroundColor: 'lightgrey',
                    borderRadius: '3px',
                    position: 'relative', 
                    border: '1px solid darkgrey',
                }}>
                    <div style={{
                        position: 'relative',
                        width: `${progress}%`,
                        height: '13px',
                        backgroundColor: 'green',
                        borderRadius: '3px',
                    }}>
                        
                        <span style={{
                            position: 'absolute', 
                            top: '50%', 
                            left: '50%', 
                            transform: 'translate(-50%, -50%)', 
                            color: 'white', 
                            fontSize: '0.8em',
                        }}>
                            {progress}%
                        </span>
                    </div>
                </div>
            </div>
        </div>
    );
}
