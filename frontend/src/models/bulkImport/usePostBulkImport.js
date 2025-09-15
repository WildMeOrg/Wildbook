import { useState, useCallback } from "react";
import { client } from "../../api/client";

export default function usePostBulkImport() {
  const [isLoading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const submit = useCallback(
    async (
      bulkImportId,
      fieldNames,
      rows,
      fileName,
      locationID,
      skipDetection,
      skipIdentification,
    ) => {
      const rowsWithNulls = rows.map((row) => {
        const newRow = { ...row };
        Object.keys(newRow).forEach((key) => {
          if (newRow[key] === "") {
            newRow[key] = null;
          }
        });
        return newRow;
      });

      const data = rowsWithNulls.map((row) =>
        fieldNames.map((fieldName) => {
          const val = row[fieldName];
          return val;
        }),
      );

      const payload = {
        bulkImportId: bulkImportId,
        fieldNames,
        rows: data,
        sourceName: fileName,
        matchingLocations: locationID,
        skipDetection: skipDetection,
        skipIdentification: skipIdentification,
        processInBackground: true,
      };

      setLoading(true);
      try {
        const resp = await client.post("/bulk-import", payload);
        return resp;
      } catch (err) {
        setError(err);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  return { submit, isLoading, error };
}
