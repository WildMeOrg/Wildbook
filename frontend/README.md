## Frontend Setup
The frontend is currently split between native tomcat functions, jsp pages, and a react app. New development is targeting a full react app rewrite. This setup is focused on the react specific requirements. However, you must do the full system setup referenced in the [development README](../devops/README.md) for these to work.

### Build and deploy react-only changes
If you are working on react-only work, you can test your changes without updating the full war file.

#### Use npm to build and deploy to your local deployment
If you have your dev environment set up correctly, this will build the React app and copy it into your local deployment directory for you.
1. cd to `REPO/frontend/`
2. run `npm run deploy-dev`
3. refresh your browser page by visiting either `http://localhost:81/react/` for local testing or `https://public.url.example.com/react/` for the public-facing deployment

#### Manually build and deploy
1. cd to `REPO/frontend/` and run `npm run build`
2. copy everything under `frontend/build/` to the deployed `wildbook/react/` directory you created during setup
3. refresh your browser page by visiting either `http://localhost:81/react/` for local testing or `https://public.url.example.com/react/` for the public-facing deployment
