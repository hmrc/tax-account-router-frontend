# Running the HMRC Tax Platform in Docker

Uses Docker and Docker-Compose to run the HMRC Digital Tax Platform.

* repeatable, consistant and documented infrastrucutre
* runs in Prod mode/replicates live configuration
* the full stack can now be tested and run locally without limitations (previously not possible)
* removes the need for a hosted dev environment, saving cost, time and maintenance
* keeps developer machines clean
* uses well established open source tools
* docker-compose allows for easy build and configuration of environments using a convenient DSL

## Prerequistes

1. [Install Docker](https://docs.docker.com/installation/) (use [boot2docker](https://docs.docker.com/installation/mac/) for MacOS)
2. Update your Docker Host to have at least 3.5GB of RAM (open VirtualBox and set this in your VirtualBox VM boot2docker image)
3. [Install Docker Compose](https://docs.docker.com/compose/install/)
3. Update your hosts file with your Docker Instance IP Address, e.g.
```
	192.168.59.103 dev.tax.service.gov.uk
```

## Setting up

1. Start the default docker machine
   ```docker-machine start default```
2. Set up your environment (you can add it to .bash_profile)
   ```eval "$(docker-machine env default)"```

## Running

In the business-tax-account/docker directory run:
```
docker-compose up
```

It's as easy as that, navigate to http://dev.tax.service.gov.uk/account/sign-in, you should be greeted with the company auth frontend login page.

## Nginx and Assets Frontend

The nginx container hosts static content for all versions of released assets.  These are dynamically downloaded during the docker build phase.  If at any point there is a new frontend assets released and you want to make sure your image has the latest versions, run the following command:
```
docker-compose build --no-cache nginx
```

## Development

You can make a microservice run from source by adding the USE_SOURCE environment variable, please make sure you add a VOLUME mapping so the docker container can access your local development copy. An example businesstax docker-compose configuration is below.  

```
businesstax:
    build: microservice-generic
    environment:
       - MICROSERVICE=business-tax-account
       - DOCKER_HOST_IP=dev.tax.service.gov.uk
       - CUSTOMARGS=-Dgovuk-tax.Prod.portal.url=http://$DOCKER_HOST_IP/portal
       - USE_SOURCE=true
    volumes:
       - /Users/ramanallamilli/Development/workspaces/hmrc/business-tax-account:/root/app
    links:
       - datastream:datastream.service
       - ytastubs:portal
       - auth:auth.service
       - gg:government-gateway.service
       - authenticator:authenticator.service
       - sa:sa.service
       - vat:vat.service
       - ct:ct.service
       - epaye:epaye.service

```

Ideally we don't want to hardcode the workspace directory, however env vars are not currently supported in Docker Compose YAML, Git issue exists for this functionality.
https://github.com/docker/fig/issues/495

## Running business-tax-account locally

Business Tax Account can be run locally whilst running everything else in Docker, this is very handy for development.  In order to do this we can modify our nginx mappings to point to our local development machine.  This step can be moved to the yml configuration once [docker/compose/issues#754](https://github.com/docker/compose/pull/848) is resolved.    

In `nginx-local-dev/etc/nginx/conf.d/default.conf` replace `DEV_MACHINE_IP` with your host IP address
```
proxy_set_header           Host  http://DEV_MACHINE_IP:9000;
proxy_pass                 http://DEV_MACHINE_IP:9000;
```

Now update your `/etc/hosts` file so it can resolve dependent services:
```
sudo ./bin/add-hosts.sh `eval docker-machine ip default`
```

Run business-tax:
```
./bin/local-sbt-prod-docker.sh
```

Run the `docker-compose-local-dev profile:
```
docker-compose -f docker-compose-local-dev.yml up
```

## Further work

- Bring the nginx configuration closer to live (ideally use the same source)
- add contact-frontend
- Introduce a convienent way for developers to be able to develop, this should be achievable by having a generic-microservice-dev container that runs the microservice from sbt in continous compile mode.  A mounted volume can be used to share the workspace so IntelliJ can be used on the host machine.
- Improve and extend this documentation (incl. how to see logs, how to check the mongo DB collections, how to start/stop/restart a single service, how to get the latest SNAPSHOTS of the microservices)
- Run integration tests against deployed environment

## Further reading

https://docs.docker.com/compose/
https://docs.docker.com/
