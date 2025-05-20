# Wildbook

Wildbook is an open source software framework to support mark-recapture, molecular ecology, and social ecology studies. The biological and statistical communities already support a number of excellent tools, such as Program MARK,GenAlEx, and SOCPROG for use in analyzing wildlife data. Wildbook is a complementary software application that:

- provides a scalable and collaborative platform for intelligent wildlife data storage and management, including advanced, consolidated searching
- provides an easy-to-use software suite of functionality that can be extended to meet the needs of wildlife projects, especially where individual identification is used
- provides an API to support the easy export of data to cross-disciplinary analysis applications (e.g., GenePop ) and other software (e.g., Google Earth)
- provides a platform that supports the exposure of data in biodiversity databases (e.g., GBIF and OBIS)
- provides a platform for animal biometrics that supports easy data access and facilitates matching application deployment for multiple species

## Getting Started with Wildbook
Wildbook is a long-standing tool that support a wide variety of researchers and species. The Wild Me team is working on revamping the tool as a true open source project, so if you have ideas and are excited to help, reach out to us on the [Wild Me Development Discord](https://discord.gg/zw4tr3RE4R)!

## Pull Request Workflow
All contributions should be made from a fork off of the Wildbook repo. While there are a number of repositories for specific Wildbook communities, large scale development is driven from the main repository. 

### Fork Wildbook
To start, you will need to be signed in to your GitHub account, have admin access to your OS's terminal, and have Git installed.
1. From your browser, in the top right corner of the [Wildbook repo](https://github.com/WildMeOrg/Wildbook), click the **Fork** button. Confirm to be redirected to your own fork (check the url for your USERNAME in the namespace).
1. In your terminal, enter the command `git clone https://github.com/USERNAME/Wildbook`
1. Once the Wildbook directory becomes available in your working directory, move to it with the command `cd Wildbook`
1. Add a reference to the original repo, denoting it as the upstream repo.
```
git remote add upstream https://github.com/WildMeOrg/Wildbook
git fetch upstream
```

### Create Local Branch
You will want to work in a branch when doing any feature development you want to provide to the original project.
1. Verify you are on the main branch. The branch you have checked out will be used as the base for your new branch, so you typically want to start from main.
`git checkout main`
1. Create your feature branch. It can be helpful to include the issue number (ISSUENUMBER) you are working to address.
`git branch ISSUENUMBER-FEATUREBRANCHNAME`
1. Change to your feature branch so your changes are grouped together.
`git checkout ISSUENUMBER-FEATUREBRANCHNAME`
1. Update your branch (this is not needed if you just created new branch, but is a good habit to get into).
` git pull upstream main`

### Set Up Development Environment with Docker
For easiest development, you will need to set up your development environment to work with Docker. See [`devops/README.md`](devops/README.md) for detailed instructions.

### Making Local Changes
Make the code changes necessary for the issue you're working on. You will need to either redeploy your war file (see [`devops/README.md`](devops/README.md)) or redeploy your front end directly (see [`frontend.README.md`](frontend/README.md)) for testing locally. 

The following git commands may prove useful.
* `git log`: lastest commits of current branch
* `git status`: current staged and unstaged modifications
* `git diff --staged`:  the differences between the staging area and the last commit
* `git add <filename>: add files that have changes to staging in preparation for commit
* `git commit`: commits the stagged files, opens a text editor for you to write a commit log

### Unit Tests
We are working on building up test coverage. Current requirements are:
* Do not drop the percentage of test coverage (exceptions will be made for large scale changes on case-by-case basis)
* Do not break existing tests

See [test coverage guidelines](src/test/README.md) for how to develop your tests.

### Submit PR
Up to this point, all changes have been done to your local copy of Wildbook. You need to push the new commits to a remote branch to start the PR process.

1. Now's the time clean up your PR if you choose to squash commits, but this is not required. If you're looking for more information on these practices, see this [pull request tutorial](https://yangsu.github.io/pull-request-tutorial/).
1. Push to the remote version of your branch ` git push <remote> <local branch>`
`git push origin ISSUENUMBER-FEATUREBRANCHNAME`
1. When prompted, provide your username and GitHub Personal Access Token. If you do not have a GitHub Personal Access Token, or do not have one with the correct permissions for your newly forked repository, you will need to [create a Personal Access Token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token).
1. Check the fork's page on GitHub to verify that you see a new branch with your added commits. You should see a line saying "This branch is X commits ahead" and a **Pull request** link. 
1. Click the **Pull request** link to open a form that says "Able to merge". (If it says there are merge conflicts, go the  for help).
1. Use an explicit title for the PR and provide details in the comment area. Details can include text, or images, and should provide details as to what was done and why design decisions were made.
1. Click **Create a pull request**. 
 
### Respond to feedback
At this point, it's on us to get you feedback on your submission! Someone from the Wild Me team will review the project and provide any feedback that may be necessary. If changes are recommended, you'll need to checkout the branch you were working from, update the branch, and make these changes locally.

1. `git checkout ISSUENUMBER-FEATUREBRANCHNAME`
1. `git pull upstream main`
1. Make required changes
1. `git add <filename>` for all files impacted by changes
1. Determine which method would be most appropriate for updating your PR  
  * `git commit --ammend` if the changes are small stylistic changes
  * `git commit` if the changes involved significant rework and require additional details

## Machine Learning in Wildbook

Wildbook leverages [Wildbook IA (WBIA)](https://github.com/WildbookOrg/wildbook-ia) as the machine learning engine, which pulls data from Wildbook servers to detect features in images and identify individual animals. WBIA brings massive-scale computer vision to wildlife research.

## Need direct help?

Wild Me (wildme.org) engineering staff provide support for Wildbook. You can contact us at: opensource@wildme.org

We provide support during regular office hours on Mondays and Tuesdays.

## Support resources
* User documentation is available at [Wild Me Documentation](http://wildbook.docs.wildme.org)
* For user support, visit the [Wild Me Community Forum](https://community.wildme.org)
* For contribution guidelines, visit [Wildbook Code Contribution Guidelines](https://wildbook.docs.wildme.org/contribute/code-guide.html)
* For developer support, visit the [Wild Me Development Discord](https://discord.gg/zw4tr3RE4R)
* Email the team at opensource@wildme.org

## History
Wildbook started as a collaborative software platform for globally-coordinated whale shark (Rhincodon typus ) research as deployed in the Wildbook for Whale Sharks (now part of http://www.sharkbook.ai). After many requests to use our software outside of whale shark research, it is now an open source, community-maintained standard for mark-recapture studies.


Wildbook is a trademark of [Conservation X Labs](https://conservationxlabs.com/), a 501(c)(3) non-profit organization, and is supported by the [Wild Me](https://wildme.org) team.
