

export default function Avatar() {
    return (
        <div className="content col-1"
            style = {{
                display: 'flex',
                justifyContent: 'space-around',
                alignItems: 'center'
            
            }}
        >
            <img src="/react/wildbook.png" alt="img"
                style = {{
                    width: '40px',
                    height: '40px',
                    borderRadius: '50%'
                }}
            />
        </div>
    );
}