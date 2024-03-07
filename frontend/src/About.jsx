
import React from 'react';
import { useState, useEffect } from 'react';
import { Button, Table, Alert } from 'react-bootstrap';


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
