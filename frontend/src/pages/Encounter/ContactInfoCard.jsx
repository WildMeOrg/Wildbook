import React from 'react';
import TrashCanIcon from '../../components/icons/TrashCanIcon';

export default function ContactInfoCard({
    title = "Contact Information",
    data = "No data available",
    onDelete = () => { },
}) {

    return (
        <div
            id={title}
            style={{
                padding: "20px",
                borderRadius: "5px",
                marginBottom: "10px",
                boxShadow: "0 4px 4px rgba(0,0,0,0.1)",
                position: "relative",
            }}
        >

            <h6>{title}</h6>
            <div className="mt-3 mb-3">
                {data?.map((item, index) => {
                    return (
                        <div 
                            key={index}
                            className="d-flex flex-row align-items-center mb-2"
                        >                           
                            <span className="avatar">
                                <i
                                    className="bi bi-person-circle"
                                    style={{ fontSize: "1.5rem", color: "#6c757d", marginRight: "10px" }}
                                ></i>
                            </span>
                            <div> {item}</div>
                        </div>
                    )
                })}
            </div>
        </div>
    )
}