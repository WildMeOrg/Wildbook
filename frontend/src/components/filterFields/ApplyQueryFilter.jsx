import React from 'react';
import { Form, FormControl } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import Button from 'react-bootstrap/Button';
import { useIntl } from 'react-intl';
import ThemeColorContext from '../../ThemeColorProvider';
import { useContext } from 'react';
import { useNavigate } from 'react-router-dom';

export default function ApplyQueryFilter() {
    const intl = useIntl();
    const theme = useContext(ThemeColorContext);
    const [queryId, setQueryId] = React.useState('');
    const navigate = useNavigate();
    return (
        <div>
            <h3><FormattedMessage id="APPLY_SEARCH_ID" /></h3>
            {/* <Description>
                <FormattedMessage id="FILTER_METADATA_DESC" />
            </Description> */}

            <p><FormattedMessage id="APPLY_SEARCH_ID_DESC"/></p>

            <Form className="d-flex flex-row w-100"
                style={{
                    height: "40px",
                }}>
                <FormControl
                    type="text"
                    placeholder="Search ID"
                    style={{
                        borderRadius: " 5px 0 0 5px",
                    }}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter' && queryId) {
                            window.location.href = `/encounter-search?searchQueryId=${queryId}`;
                        }

                    }}
                    onChange={(e) => {
                        setQueryId(e.target.value);
                    }
                    }
                />
                <Button
                    style={{
                        height: "40px",
                        border: `1px solid white`,
                        borderRadius: "0 5px 5px 0",
                        backgroundColor: theme.primaryColors.primary700,
                    }}
                    onClick={() => {
                        if (queryId) {
                            // navigate(`/encounter-search?searchQueryId=${queryId}`);
                            window.location.href = `/react/encounter-search?searchQueryId=${queryId}`;
                        }
                    }
                    }
                >
                    <FormattedMessage id="APPLY"/>
                </Button>
            </Form>
        </div>

    );
}