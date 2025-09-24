import React from 'react';
import TrashCanIcon from '../../components/icons/TrashCanIcon';
import { observer } from 'mobx-react-lite';

export const ContactInfoCard = observer(({
    title = "Contact Information",
    type = "submitter",
    data = [],
    store = {},
}) => {

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
                                {item.image ? (
                                    <img
                                        src={item.image}
                                        alt="Avatar"
                                        style={{
                                            width: "30px",
                                            height: "30px",
                                            borderRadius: "50%",
                                            marginRight: "10px",
                                        }}
                                    />)
                                    :
                                    <i
                                        className="bi bi-person-circle"
                                        style={{ fontSize: "1.5rem", color: "#6c757d", marginRight: "10px" }}
                                    ></i>}
                            </span>
                            <div> {item?.displayName}</div>
                            <div style={{marginLeft: "auto",
                                cursor: "pointer",
                            }}
                                onClick={() => {
                                    store.removeContact(type, item.id);
                                }}
                            >
                                <TrashCanIcon/>
                            </div>
                        </div>
                    )
                })}
            </div>
            
        
        </div>
    )
})

export default ContactInfoCard;