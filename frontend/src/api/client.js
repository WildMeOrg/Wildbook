import axios from "axios";

const API_BASE = process.env.REACT_APP_API_BASE_PATH;

export const client = axios.create({
  baseURL: API_BASE,
  headers: { "Content-Type": "application/json" },
});
