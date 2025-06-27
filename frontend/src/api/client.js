import axios from "axios";

const API_BASE = "/api/v3";

export const client = axios.create({
  baseURL: API_BASE,
  headers: { "Content-Type": "application/json" },
});
