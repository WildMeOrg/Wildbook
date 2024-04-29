### pre-reqs

- node, npm
- `npm install react-app-rewired`


### steps

1. create a folder `react` under deployed `wildbook/` dir
2. create a `.env` under the root of `REPO/frontend/`, add public-facing url like this:
  - `PUBLIC_URL=http://localhost:8080/react/` (for local tomcat)
  - `PUBLIC_URL=https://public.url.example.com/react/`
3. cd to `REPO/frontend/` and run `npm run build`
4. copy everything under `frontend/build/` to the `wildbook/react/` created in step 1
