import { useState } from "react";
import axios from "axios";

// Custom Hook
export default function useCreateAnnotation() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [submissionDone, setSubmissionDone] = useState(false);
  const [responseData, setResponseData] = useState(null);

  // Function to perform the Axios request
  const createAnnotation = async ({
    encounterId,
    assetId,
    ia,
    viewpoint,
    x,
    y,
    width,
    height,
    rotation,
  }) => {
    setLoading(true);
    setError(null);

    console.log("rotation", rotation);

    try {
      const response = await axios.request({
        method: "post",
        url: "/api/v3/annotations",
        data: {
          encounterId: encounterId,
          height: height,
          iaClass: ia.value,
          mediaAssetId: assetId,
          theta: rotation,
          viewpoint: viewpoint.value,
          width: width,
          x: x,
          y: y,
        },
      });

      if (response.status === 200) {
        setSubmissionDone(true);
        setResponseData(response.data);
      }
    } catch (error) {
      setError(error);

      if (error.response && error.response.status === 400) {
        setError(error.response.data.errors);
      }
    } finally {
      setLoading(false);
    }
  };

  return { createAnnotation, loading, error, submissionDone, responseData };
}
