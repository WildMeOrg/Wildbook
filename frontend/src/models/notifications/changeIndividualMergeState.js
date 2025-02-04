import axios from "axios";

export default async function changeIndividualMergeState(action, mergeId) {
  let json = {};
  json["mergeId"] = mergeId;
  json["action"] = action;

  try {
    const response = await axios.post("/ScheduledIndividualMergeUpdate", json, {
      headers: {
        "Content-Type": "application/json",
      },
    });
    return response;
  } catch (error) {
    return error;
  }
}
