import axios from "axios";

export async function postRestKeyword(payload, opts = {}) {
  const res = await axios.post(`/RestKeyword`, payload, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
  });
  return res.data; 
}

export async function getAssetKeywords(mid) {
  const res = await axios.get(`/RestKeyword`, {
    params: { assetIds: String(mid) },   
    withCredentials: true,
    headers: { Accept: "application/json" },
  });
  const data = res.data || {};
  const byMid = (data.results && data.results[mid]) || {};
  return Object.entries(byMid).map(([id, name]) => ({ id, name }));
}
export function addExistingKeyword(mid, keywordId) {
  return postRestKeyword({ onMediaAssets: { assetIds: [mid], add: [keywordId] } });
}

export function addNewKeywordText(mid, text) {
  return postRestKeyword({ onMediaAssets: { assetIds: [mid], newAdd: [text] } });
}

export function removeKeyword(mid, keywordId) {
  return postRestKeyword({ onMediaAssets: { assetIds: [mid], remove: [keywordId] } });
}
