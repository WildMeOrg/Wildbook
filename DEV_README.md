# Dev Docs Readme

Default 
username: tomcat
Password: tomcat123

## Habitat Development
Developing with habitat allows the developer to not have to install tomcat or postgres to test their code changes. 
The below steps are tested on Mac and Linux desktops. 

### Install habitat
https://www.habitat.sh/docs/install-habitat/

### Install Docker
Docker is not needed when working on a linux desktop. 
https://docs.docker.com/engine/installation/#desktop

### Install direnv
If you have Homebrew: run `brew install direnv`. This software is only used to source the .envrc file before starting
the habitat studio. Runing this command will do the same thing `source .envrc`.

### Start Development
1. First open the command terminal from the root of the project
1. Enter the habitat studio by typing `hab studio enter`
1. Once in the studio type `build` to build the Wildbook software
1. To start the project type `start`. This will first install postgres and then the Wildbook package built above with tomat. 
1. From here you can see the web page by going to http:localhost:8080/wildbook/ 

For a Mac the studio is run in a docker container, so nothing is install locally. On linux, 
everything is installed locally, but it is placed in a partition section of the OS. 
On linux you can also use a docker container.
