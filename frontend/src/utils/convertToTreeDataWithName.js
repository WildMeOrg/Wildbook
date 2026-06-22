const convertToTreeDataWithName = (locationData) => {
  if (!Array.isArray(locationData)) return [];
  return locationData.map((location) => ({
    title: location.name,
    value: location.id,
    children:
      location.locationID?.length > 0
        ? convertToTreeDataWithName(location.locationID)
        : [],
  }));
};

export default convertToTreeDataWithName;
