import React from 'react';
import 'bootstrap/dist/css/bootstrap.min.css';
import { Card, ListGroup, ListGroupItem } from 'react-bootstrap';

const PickUp = () => {
    return (
        <div style={{
            marginTop: '40px',
            position: 'relative',
            height: '500px',
        
        }}>           
            
        <div className="col-8" style ={{                    
                    padding: '20px',
                    marginTop: '20px',     
                    position: 'absolute',
                    top: '20%',
                    left: '100px',                    
                    zIndex: 1,   
                }}>
                    <h1 style={{ fontSize:36}}>Pick up where you left</h1>
                    
                    {/* <ListGroup>
                        <ListGroupItem>
                            Latest bulk report
                            <span className="text-muted"> 126 files uploaded | 2nd Jun 2023</span> 
                            <span className="float-right">{'>'}</span> 
                        </ListGroupItem>                        
                    </ListGroup> */}
                    <h4> Latest bulk report </h4>
                    <h4> Latest bulk report </h4>
                    <h6> Latest bulk report </h6>
        </div>
        <dir style={{
            backgroundColor: '#cfe2ff',
            position: 'absolute',
            top: 0,
            left: '40%',
            bottom: '10%',
            borderRadius: '10px 0 0 10px',
            width: '60%',
        }}>

        </dir>
        <div className="col-4" 
            style={{
                position: 'absolute',
                    top: '10%',
                    left: '50%',
                    width: "320px",
                    borderRadius: '10px, 0, 0, 10px',
                    width: '300px',
                    zIndex: 1,
                }}
            >
                <Card>
                    <Card.Img variant="top" src="./pick.png" borderRadius="10px"/>
                </Card>
        </div>
           
        </div>
    );
};

export default PickUp;
