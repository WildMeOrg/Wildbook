const convertToTreeData = (locationData) => {
  if (!Array.isArray(locationData)) return [];
  return locationData.map((location) => ({
    title: location.id,
    value: location.id,
    children:
      location.locationID?.length > 0
        ? convertToTreeData(location.locationID)
        : [],
  }));
};

export default convertToTreeData;
