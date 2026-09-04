# Wildbook Catalog Helper — Toolbox

These tools help you check and tidy up your animal photo-ID catalog: spot the same animal recorded
twice, find sightings filed under the wrong animal, and judge how reliable the automatic matching
is. Everything here is **read-only** — the tools only ever suggest; you make the changes in Wildbook.

## What you'll need

A short-lived access token from Wildbook. In Wildbook, open your account menu and choose
**API Access** to create one, then paste **only that token** to your assistant — never your username
or password. The token has an expiration date that may vary by Wildbook; create a fresh one when it stops working.
Full technical detail is in the **api-reference** page (fetch `/api/v3/agent-skill/api-reference`).

## The toolbox

| Tool | Use this when you want to… | Fetch |
|---|---|---|
| find-missed-matches | check whether the same animal was recorded twice under different names | `/api/v3/agent-skill/find-missed-matches` |
| find-misfiled-sightings | check whether any sightings are filed under the wrong animal | `/api/v3/agent-skill/find-misfiled-sightings` |
| how-good-is-our-matching | understand how reliable the automatic matching is for a species or site | `/api/v3/agent-skill/how-good-is-our-matching` |
| review-id-problems | go through suspected ID problems photo-by-photo and build a to-do list | `/api/v3/agent-skill/review-id-problems` |

## How this works

When you describe one of the tasks above to your AI assistant, it fetches that tool's page and follows the steps there. Each page tells the assistant exactly which catalog information to look up and how to show you what it finds — so you can review and decide.

## Additional references

These tools are only a subset of the types of data management and scientific analysis tasks you can use this API and Wildbook. For more information about Wildbook, see these resources if needed:
- [Wildbook Documentation](https://wildbook.docs.wildme.org/)
- [Wildbook Community](https://community.wildme.org/)
- [How I AI by Wildbook User Dr. Simon Pierce](https://github.com/simonjpierce/how-i-ai)
