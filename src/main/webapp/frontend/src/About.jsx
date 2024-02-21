
import React from 'react';
import { useState, useEffect } from 'react';
import { Button, Table, Alert } from 'react-bootstrap';
import 'bootstrap/dist/css/bootstrap.min.css';

export default function About() {
    const [data, setData] = useState([]);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await fetch('/wildbook/fakeApi.jsp');
                const jsonData = await res.json();
                setData(jsonData);
            } catch (error) {
                console.error('Error fetching data:', error);
            }
        };

        fetchData();
    }, []);

    console.log(data);
      
    return (
        <div>
        <h1>About</h1>
        <Alert>
            This is a simple page about wildbook.
        </Alert>

        <div className="clip-image-wrapper">
        <div className="clip-image" style={{ backgroundImage: `url('https://sb.ecobnb.net/app/uploads/sites/3/2022/04/copertina-1.jpg')` }}>
          <h1>Submit</h1>
          <p>Lorem ipsum dolor sit amet consectetur. Euismod turpis sed feugiat ullamcorper.</p>
          <button>Report a Sighting</button>
          <button>Bulk Report</button>
        </div>
      </div>


      <div className="card-container">
      <div className="card">
        <div className="card-content">
          {/* <YourIcon className="card-icon" /> */}
          <div className="card-date">Aug 05 2023</div>
          <div className="card-description">Lorem ipsum</div>
          <div className="card-annotations">5 annotations</div>
          <div className="card-animals">2 animals</div>
        </div>
        <div className="card-decoration-point top-left"></div>
        <div className="card-decoration-point bottom-right"></div>
      </div>
    </div>

  <svg width="538" height="526" viewBox="0 0 538 526" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <pattern id="backgroundImage" patternUnits="userSpaceOnUse" width="538" height="526">
      <image href="https://sb.ecobnb.net/app/uploads/sites/3/2022/04/copertina-1.jpg" x="0" y="0" width="538" height="526" />
    </pattern>
  </defs>
<path d="M361.294 100.774C448.672 151.222 449.982 430.013 369.842 473.866C289.702 517.719 35.7239 358.94 33.9135 279.918C32.103 200.895 273.916 50.3266 361.294 100.774Z" fill="#D9D9D9"/>
</svg>

<svg width="538" height="526" viewBox="0 0 538 526" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <pattern id="backgroundImage" patternUnits="userSpaceOnUse" width="538" height="526">
      <image href="https://sb.ecobnb.net/app/uploads/sites/3/2022/04/copertina-1.jpg" x="0" y="0" width="538" height="526" />
    </pattern>
  </defs>
  <path d="M361.294 100.774C448.672 151.222 449.982 430.013 369.842 473.866C289.702 517.719 35.7239 358.94 33.9135 279.918C32.103 200.895 273.916 50.3266 361.294 100.774Z" fill="url(#backgroundImage)"/>
</svg>

<svg width="538" height="526" viewBox="0 0 538 526" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <pattern id="backgroundImage" patternUnits="userSpaceOnUse" width="538" height="526">
      <image href="https://sb.ecobnb.net/app/uploads/sites/3/2022/04/copertina-1.jpg" x="0" y="0" width="538" height="526" preserveAspectRatio="none"/>
    </pattern>
  </defs>
  <path d="M361.294 100.774C448.672 151.222 449.982 430.013 369.842 473.866C289.702 517.719 35.7239 358.94 33.9135 279.918C32.103 200.895 273.916 50.3266 361.294 100.774Z" fill="url(#backgroundImage)"/>
</svg>



        <div 
            style = {{
                backgroundImage: `url('https://sb.ecobnb.net/app/uploads/sites/3/2022/04/copertina-1.jpg')`,
                height: 50,
                width: 50,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                backgroundRepeat: 'no-repeat'}
            }
            className = 'glyphicon glyphicon-triangle-top' 
            backgroundColor="black"
            >
            <h1>Wildbook</h1>
        </div>

        <Button variant="primary">Wildbook</Button>
        <Table striped bordered hover>
            <thead>
                <tr>
                <th>#</th>
                <th>Individual ID</th>
                <th>Individual Name</th>
                </tr>
            </thead>
            <tbody>
                {data.map((item, index) => (
                    <tr key={item.id}>
                        <td>{index}</td>
                        <td>{item.id}</td>
                        <td>{item.names[0]}</td>
                        
                    </tr>
                ))
                }
            </tbody>
            
         </Table>

        </div>
    );
}
