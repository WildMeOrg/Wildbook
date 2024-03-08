
import React from "react";
import Progress from './Progress';
import { Link } from 'react-router-dom';

export default function Projects ({ data }) {

    console.log(data);
    console.log(data?.projects);
    
    return <div style={{
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        width: '100%',
        height: '400px',
        padding: '20px',
    
    }}>
        <div style={{
            width: '45%',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'flex-end',
    }}>
            <h1 style={{                
                fontSize: '4em',
            }}>
                View your
            </h1>
            <h1 style={{                
                fontSize: '4em',
            }}>
                projects
            </h1>
        </div>
        <div style={{
            width: '45%',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'flex-start',
            padding: '20px',
        }}>
           
            { 
                Array.isArray(data?.projects) && data?.projects.length &&
                data?.projects.map((item, index) => {
                        return <Progress 
                            key={index}
                            name={item.name} 
                            encounters = {item.numberEncounters} 
                            progress={item.percentComplete}
                            href={`projects/project.jsp?id=${item.id}`}
                            noUnderline
                            newTab
                            />
                    })
                }

                <Progress 
                    name='Amphibians & Reptiles' 
                    encounters = '126' 
                    progress='25'
                    href='/projects/projectList.jsp'
                    noUnderline
                    newTab
                    />         
           
                <Progress 
                    name='Seal' 
                    encounters = '12' 
                    progress='12'
                    href='/projects/projectList.jsp'
                    noUnderline
                    newTab
                    />             
           
                <Progress 
                        name='Seal' 
                        encounters = '12' 
                        progress='12'
                        href='/projects/projectList.jsp'
                        noUnderline
                        newTab
                        />
          

        </div>

    </div>



}