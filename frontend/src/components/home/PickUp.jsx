import React from 'react';
import 'bootstrap/dist/css/bootstrap.min.css';
import LatestActivityItem from './LatestActivityItem';

const PickUp = () => {
    return (
        <div style={{
            marginTop: '40px',
            position: 'relative',
            height: '500px',        
        }}>           
            
        <div className="col-8" style ={{                    
                    padding: '10px',
                    position: 'absolute',
                    left: '100px',
                    width: '500px',                    
                    zIndex: 1,   
                }}>
                    <h1 style={{ fontSize:'4em',
                    }}>Pick up where</h1>
                    <h1 style={{ fontSize:'4em',
                    }}>
                    you left
                    </h1>
                <LatestActivityItem
                    name="Latest bulk report"
                    files="125"
                    date="Aug 05 2023"
                />
                <LatestActivityItem
                    name="Latest Individual"
                    files="12"
                    date="Aug 05 2023"
                />
                <LatestActivityItem
                    name="Latest Matching Action"
                    files="25"
                    date="Aug 05 2023"
                />
                    
        </div>
        <div style={{
            backgroundColor: '#cfe2ff',
            position: 'absolute',
            top: 0,
            left: '40%',
            bottom: '10%',
            borderRadius: '10px 0 0 10px',
            width: '60%',
        }}>
        </div>
        <div className="col-4" 
            style={{
                position: 'absolute',
                    top: '10%',
                    left: '55%',
                    width: '300px',
                    borderRadius: '10px',
                    height: '450px',
                    zIndex: 1,   
                    backgroundImage: 'url(/wildbook/react/pick.png)',                 
                }}
            >
                
                    {/* <img src="/wildbook/react/pick.png" borderRadius="10px"/> */}
              
        </div>
           
        </div>
    );
};

export default PickUp;
