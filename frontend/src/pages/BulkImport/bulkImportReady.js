// Next, the step bar and Start Import all use this: a parsed sheet with at least one
// data row, every required column, and no validation errors
export const isReadyToImport = (store) =>
  store.spreadsheetUploadProgress === 100 &&
  (store.spreadsheetData?.length ?? 0) > 0 &&
  Object.keys(store.validationErrors ?? {}).length === 0 &&
  (store.missingRequiredColumns?.length ?? 0) === 0;
