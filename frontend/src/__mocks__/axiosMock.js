import axios from "axios";

export const mockAxiosSuccess = (mockedData) => {
  axios.request.mockResolvedValue({ data: mockedData });
};

export const mockAxiosFailure = () => {
  axios.request.mockRejectedValue(new Error("Network error"));
};
