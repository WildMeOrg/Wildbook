import React, { useState } from 'react';
import { useIntl } from 'react-intl';
import { Modal, Button as BootstrapButton, Form, InputGroup } from 'react-bootstrap';
import Slider from 'rc-slider';
import 'rc-slider/assets/index.css';
import { FormattedMessage } from 'react-intl';

import PointDistanceMap from './mapUtils/PointDistanceMap';
// import Button from '../Button';
import Text from '../Text';

const inputWidth = 220;

export default function PointDistanceFilter({
  label,
  labelId,
  description,
  descriptionId,
  filterId,
  defaultDistance = 50,
  clause = 'filter',
  queryTerm,
  onChange,
  style,
  nested = false,
  ...rest
}) {
  const intl = useIntl();

  const [distance, setDistance] = useState(defaultDistance);
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [gpsForMapUpdate, setGpsForMapUpdate] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);

  const onClose = () => setModalOpen(false);

  const translatedLabel = labelId
    ? (intl.messages[labelId]
      ? intl.formatMessage({ id: labelId })
      : labelId)
    : label;

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        ...style,
      }}
    >
      <Text style={{ color: '#6c757d' }}>
        {translatedLabel}
      </Text>
      <BootstrapButton
        size="sm"
        onClick={() => setModalOpen(true)}
        style={{ marginLeft: 8, minWidth: 48, height: 36 }}
        id="SET"
        {...rest}
      >
        Set
      </BootstrapButton>
      <Modal show={modalOpen} onHide={onClose}>
        <Modal.Header closeButton>
          <Modal.Title>
            <FormattedMessage id="SELECT_LOCATION" />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {modalOpen && (
            <PointDistanceMap
              latitude={latitude}
              longitude={longitude}
              gps={gpsForMapUpdate}
              distance={distance}
              onChange={({ lat, lng }) => {
                setLatitude(lat.toString());
                setLongitude(lng.toString());
              }}
            />
          )}
          <Text
            id="SEARCH_CENTER_POINT"
            variant="h6"
            style={{ marginTop: 12 }}
          />
          <InputGroup className="mb-3">
            <Form.Control
              style={{ width: inputWidth, marginRight: 16 }}
              id="gps-latitude"
              placeholder={intl.formatMessage({ id: 'DECIMAL_LATITUDE' })}
              value={latitude}
              onChange={e => {
                const inputValue = e.target.value;
                setLatitude(inputValue);
                if (longitude)
                  setGpsForMapUpdate([inputValue, longitude]);
              }}
            />
            <Form.Control
              style={{ width: inputWidth }}
              id="gps-longitude"
              placeholder={intl.formatMessage({ id: 'DECIMAL_LONGITUDE' })}
              value={longitude}
              onChange={e => {
                const inputValue = e.target.value;
                setLongitude(inputValue);
                if (latitude)
                  setGpsForMapUpdate([latitude, inputValue]);
              }}
            />
          </InputGroup>
          <Text
            id="SEARCH_RADIUS_LABEL"
            variant="h6"
            style={{ marginTop: 12 }}
          />
          <div style={{ marginTop: 48, width: inputWidth * 2 + 16 }}>
            <Slider
              min={1}
              max={500}
              value={distance}
              onChange={newDistance => {
                setDistance(newDistance);
              }}
              railStyle={{ backgroundColor: '#d9d9d9' }}
              handleStyle={{
                borderColor: '#1890ff',
                height: 14,
                width: 14,
                marginLeft: -7,
                marginTop: -3,
                backgroundColor: '#fff',
              }}
              trackStyle={{ backgroundColor: '#1890ff' }}
            />
            <div className="mt-2 text-center">{distance} km</div>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <BootstrapButton variant="secondary" onClick={onClose}>
            <FormattedMessage id="CANCEL" />
          </BootstrapButton>
          <BootstrapButton
            variant="primary"
            onClick={() => {
              onChange({
                filterId,
                descriptor: `${translatedLabel}: ${distance}km`,
                nested,
                clause,
                query: {
                  'geo_distance': {
                    'distance': `${distance}km`,
                    [queryTerm]: {
                      'lat': parseFloat(latitude),
                      'lon': parseFloat(longitude),
                    },
                  },
                },
              });
              onClose();
            }}
            id="CONFIRM"
          >
            <FormattedMessage id="CONFIRM" />
          </BootstrapButton>
        </Modal.Footer>
      </Modal>
    </div>
  );
}
