import { useState, useCallback } from "react";
import { client } from "../../api/client";

export default function usePostBulkImport() {
  const [isLoading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const submit = useCallback(async (bulkImportId, fieldNames, rows) => {
    const data = rows.map((row) =>
      fieldNames.map((fieldName) => {
        const val = row[fieldName];
        return val;
      }),
    );
    const payload = {
      bulkImportId: bulkImportId,
      fieldNames,
      rows: data,
    };

    setLoading(true);
    try {
      const resp = await client.post("/bulk-import", payload);
      return resp.data;
    } catch (err) {
      setError(err);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { submit, isLoading, error };
}
