# Wildbook Helper — Toolbox

These tools help you work with your animal photo-ID catalog in two ways: **check and tidy** the
catalog you already have, and **get new sightings into** it. Each tool's page tells your assistant
exactly what to do; you review and make the final decisions in Wildbook.

## What you'll need

Most tools here need a short-lived access token from Wildbook. In Wildbook, open your account menu
and choose **API Access → Read data** to create one, then paste **only that token** to your assistant — never
your username or password. The token has an expiration date that may vary by Wildbook; create a
fresh one when it stops working. Full technical detail is in the **api-reference** page (fetch
`/api/v3/agent-skill/api-reference`). The import-prep tools below are the exception — they need no
token, because they only prepare files you upload yourself. Direct API submission is a separate,
limited pilot: a site administrator must assign your account the **api-submission** role.
Then choose **API Access → Data import** and confirm your password to create a
**submissions:write** token. The **Read data** token does not grant submission access,
and the **Data import** token does not work with the general read API.

## Check and tidy your catalog (read-only — the tools only suggest; you make the changes in Wildbook)

| Tool | Use this when you want to… | Fetch |
|---|---|---|
| find-missed-matches | check whether the same animal was recorded twice under different names | `/api/v3/agent-skill/find-missed-matches` |
| find-misfiled-sightings | check whether any sightings are filed under the wrong animal | `/api/v3/agent-skill/find-misfiled-sightings` |
| how-good-is-our-matching | understand how reliable the automatic matching is for a species or site | `/api/v3/agent-skill/how-good-is-our-matching` |
| review-id-problems | go through suspected ID problems photo-by-photo and build a to-do list | `/api/v3/agent-skill/review-id-problems` |

## Get sightings into Wildbook (import prep — no token needed)

| Tool | Use this when you want to… | Fetch |
|---|---|---|
| inat-to-wildbook-import | pull recent iNaturalist sightings of your species and prepare them for bulk import | `/api/v3/agent-skill/inat-to-wildbook-import` |

## Submit sightings directly (enrolled pilot — creates records when committed)

| Tool | Use this when you want to… | Fetch |
|---|---|---|
| submit-sightings | send photographs and sighting data through the submissions API, fix validation errors, and retrieve imported record IDs | `/api/v3/agent-skill/submit-sightings` |

This tool stages and validates your data first. Commit only within the person's authorized scope;
if they asked for a preview or preparation only, show the preview without committing.
New submissions request detection and individual matching by default; explicit
`import-only` skips both. The skill documents exact input fields, examples, processing
choices and recovery after interrupted requests.

## How this works

When you describe one of the tasks above to your AI assistant, it fetches that tool's page and
follows the steps there. Each page tells the assistant exactly what to look up or run and how to
show you what it finds — so you can review and decide.

## Additional references

These tools are only a subset of the data management and scientific analysis tasks you can do with
this API and Wildbook. For more information about Wildbook, see:
- [Wildbook Documentation](https://wildbook.docs.wildme.org/)
- [Wildbook Community](https://community.wildme.org/)
- [How I AI by Wildbook User Dr. Simon Pierce](https://github.com/simonjpierce/how-i-ai)
