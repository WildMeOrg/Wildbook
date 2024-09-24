### pre-reqs
- node, npm
- `npm install react-app-rewired`

### steps to setup dev environment
1. cd to the root folder of your codebase, for example `/Wildbook`
2. run `npm install` 
3. run `chmod +x .husky/pre-commit` to enable husky pre-commit hooks
4. cd to the react folder `Wildbook/frontend/`,
5. also run `npm install` to install all dependencies
6. now you should be able to commit and Husky will check your code for any issues before each commit
7. create a `.env` for React environment variables under the root of `REPO/frontend/`. In this file:
    1. Add the public URL. For local tomcat development, use `PUBLIC_URL=http://localhost:81/react/` (or whatever port your local server is running on). For public deployment, use the following, where `public.url.example.com` is your deployed URL: `PUBLIC_URL=https://public.url.example.com/react/`
    2. Add site name like this: `SITE_NAME=Amphibian Wildbook`

### steps to set up deploy directory
1. create a folder `react` under deployed `wildbook/` dir

### steps to build and deploy react
#### using npm to build and deploy to your local deployment
If you have your dev environment set up correctly, this will build the React app and copy it into your local deployment directory for you.
1. cd to `REPO/frontend/`
2. run `npm run deploy-dev`
3. refresh your browser page by visiting either `http://localhost:81/react/` for local testing or `https://public.url.example.com/react/` for the public-facing deployment

#### manually building and deploying
1. cd to `REPO/frontend/` and run `npm run build`
2. copy everything under `frontend/build/` to the deployed `wildbook/react/` directory you created during setup
3. refresh your browser page by visiting either `http://localhost:81/react/` for local testing or `https://public.url.example.com/react/` for the public-facing deployment
