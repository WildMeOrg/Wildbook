import React from 'react';
import { Form, FormControl } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import Description from '../Form/Description';
import Button from 'react-bootstrap/Button';
import InputGroup from 'react-bootstrap/InputGroup';
import { useIntl } from 'react-intl';
import ThemeColorContext from '../../ThemeColorProvider';
import { useContext } from 'react';

export default function ApplyQueryFilter() {
    const intl = useIntl();
    const theme = useContext(ThemeColorContext);
    const [queryId, setQueryId] = React.useState('');
    return (
        <div>
            <h3><FormattedMessage id="Apply Query ID" /></h3>
            {/* <Description>
                <FormattedMessage id="FILTER_METADATA_DESC" />
            </Description> */}

            <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit,
                sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
                Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris
                nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in
                reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.
                Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt
                mollit anim id est laborum.  </p>

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
                            window.location.href = `/react/encounter-search?searchQueryId=${queryId}`;
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
                            // navigate(`/encounter-search?searchQueryId=${input.value || ''}`);
                            window.location.href = `/react/encounter-search?searchQueryId=${queryId}`;
                        }
                    }
                    }
                >Apply</Button>
            </Form>









        </div>

    );
}