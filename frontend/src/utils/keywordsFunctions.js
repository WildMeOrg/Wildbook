import axios from "axios";

export async function postRestKeyword(payload) {
  if (!payload) return;
  try {
    const res = await axios.post(`/RestKeyword`, payload, {
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
    });
    return res.data;
  } catch (error) {
    console.error("Error posting RestKeyword:", error);
    throw error;
  }
}

export async function postLabeledKeyword(mid, label, value) {
  if (!mid || !label || !value) return;
  try {
    const res = await axios.post("/AddLabeledKeyword", null, {
      params: { label, value, mid },
    });
    return res.data;
  } catch (error) {
    console.error("Error posting LabeledKeyword:", error);
    throw error;
  }
}

export async function getAssetKeywords(mid) {
  if (!mid) return [];
  try {
    const res = await axios.get(`/RestKeyword`, {
      params: { assetIds: String(mid) },
      withCredentials: true,
      headers: { Accept: "application/json" },
    });
    const data = res.data || {};
    const byMid = (data.results && data.results[mid]) || {};
    return Object.entries(byMid).map(([id, name]) => ({ id, name }));
  } catch (error) {
    console.error("Error fetching asset keywords:", error);
    throw error;
  }
}

export function addExistingKeyword(mid, keywordId) {
  return postRestKeyword({
    onMediaAssets: { assetIds: [mid], add: [keywordId] },
  });
}

export function addExistingLabeledKeyword(mid, label, value) {
  return postLabeledKeyword(mid, label, value);
}

export function addNewKeywordText(mid, text) {
  return postRestKeyword({
    onMediaAssets: { assetIds: [mid], newAdd: [text] },
  });
}

export function removeKeyword(mid, keywordId) {
  return postRestKeyword({
    onMediaAssets: { assetIds: [mid], remove: [keywordId] },
  });
}
