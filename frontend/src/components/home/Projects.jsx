
import React from "react";
import Progress from './Progress';
import { FormattedMessage } from "react-intl";

export default function Projects ({ data }) {

    console.log(data);
    
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
                (Array.isArray(data) && data.length) ?
                data?.map((item, index) => {
                    return <Progress 
                            key={index}
                            name={item.name} 
                            encounters = {item.numberEncounters} 
                            progress={item.percentComplete}
                            href={`/projects/project.jsp?id=${item.id}`}
                            noUnderline
                            newTab
                            />
                    })
                    : <h1>No projects found</h1>
            }

                <Progress 
                    name='Fake Project' 
                    encounters = '126' 
                    progress='25'
                    href='/projects/projectList.jsp'
                    noUnderline
                    newTab
                    />    
                <a href='/projects/projectList.jsp'
                    style={{
                        color: 'black',
                        fontWeight: '600',
                        textDecoration: 'none',     
                        marginTop: '20px',
                        fontSize: '1.1em',    
                        display: 'flex',
                        flexDirection: 'row',
                        justifyContent: 'center',
                        alignItems: 'center',           
                    }}
                    target = '_blank'
                ><FormattedMessage id='SEE_ALL'/><i class="bi bi-arrow-right-short" style={{fontSize: 22}}></i> </a>     

          

        </div>

    </div>



}