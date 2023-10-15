# Distributed-Cloud-Compute-Framework
The Distributed Cloud Compute Framework (DCCF) is a scalable distributed computing framework tailored for the cloud. More specifically, it is a Kubernetes-based architecture for running custom asynchronous dockerized tasks in a Docker-In-Docker environment.  

DCCF was created as a personal project by [prokopchukdim](https://github.com/prokopchukdim) to learn more about distributed systems, stateless APIs, containerization, event-based architectures, Docker, and Kubernetes deployments.

## Architecture
### Overview
![Distributed Compute Framework](https://github.com/prokopchukdim/Distributed-Cloud-Compute-Framework/assets/87666671/8fe8c964-fbf3-4d41-bddb-b5db5a0f4fb2)

Task Manager pods, also known as the Master API, contain a RESTful API for uploading & monitoring tasks, as well as retrieving results. The Master API is responsible for uploading task files and information to a PostgreSQL DB, and queueing up the task by ID to a Kafka Topic. Task Manager pods are easily horizontally scalable.

Task are queued up using an Apache Kafka cluster. Each broker is a separate Kubernetes pod managed by Kafka KRaft, and for fault tolerance all pods are responsible for managing one synchronized topic. The Kafka cluster can be horizontally scaled with more brokers.

Each worker pod acts as a Kafka consumer through a Springboot application. Once a task is consumed from the queue, the consuming worker is responsible for retrieving the relevant task files from PostgreSQL, updating task status in PostgreSQL, mounting and executing the dockerized task using a Docker-In-Docker architecture, and returning logs and output files to PostgreSQL. These results can then be monitored by clients through the master API. Worker pods are also horizontally scalable.

### Worker Pods
![Distributed Compute Framework - Worker](https://github.com/prokopchukdim/Distributed-Cloud-Compute-Framework/assets/87666671/92a56c1d-b134-4be4-af59-493b93c480fd)

To support custom dockerized tasks, each worker pod runs its own containerized Docker Daemon and Springboot application. By having a daemon local to each pod, all systems remain containerized. This also reduces security vulnerabilities stemming from running custom docker files on a system-wide docker daemon. The Spring application is responsible for orchestrating the docker-in-docker setup, consuming task ids from a kafka queue, cleaning up files and images in between running tasks, as well as fetching and updating task information from PostgreSQL. Since the task container is run using the daemon, a shared output volume is mounted pod-wide as both the Spring app and the task require access to the volume, and the daemon is responsible for mounting the volume to the task container.

Note: DCCF automatically mounts an output folder to every task at `/output/`. Only files in this volume will be saved and retrievable after task completion. Standard output and error streams are always saved as log files.

## Running and Testing
Running DCCF can be done on any Kubernetes cluster. An example deployment can be created locally using [minikube](https://minikube.sigs.k8s.io/docs/start/):

### Starting the cluster
```
minikube start --nodes 3
kubectl label nodes minikube-m03 node-role.kubernetes.io/master-node=master-node
kubectl label nodes minikube-m03 role=master-node
kubectl label node minikube-m02 node-role.kubernetes.io/worker=worker
kubectl label node minikube-m02 role=worker
```
Note that pods will only deploy once a correctly labeled node is available.

### Initial Launch
```
cd kube
kubectl apply -f kafka.yaml
kubectl apply -f postgres.yaml
```
Once Kafka and Postgres are running, the Kafka topic needs to be set up before connecting any master or worker pods:
```
kubectl exec -it kafka-0 -- /bin/sh
kafka-topics.sh --create --topic jobIdTopic--partitions 3 --replication-factor 3 --bootstrap-server localhost:9092
kafka-topics.sh --describe --topic jobIdTopic--bootstrap-server localhost:9092
```
Once this is complete, run `exit` to get out of the Kafka pod, and apply the rest of the services:
```
kubectl apply -f master.yaml
kubectl apply -f postgres.yaml
```
To expose the master REST API outside of the cluster, run:
```
minikube tunnel
```
DCCF should now be accessible at localhost port 80. Note some more useful commands for the kubernetes deployment are available in `kube/commands.txt`.

### Running a test job
A test dockerfile and job is available in `master/DemoResources`. If you would like to run the test job quickly to see how it works, you can send a POST request to `/submitDemo`, which returns the job ID. The job ID can then be used to monitor status with a GET request to the `/getJobStatus` endpoint, and results can be received with a GET request to the `/getResultingFiles' endpoint.

Alternatively, `master/DemoResources/demo-request.py` contains a Python script to submit the same custom job to DCCF using the fully-fledged `/submit` API endpoint, and prints the job id of the submitted job.

## Limitations and Improvements
It is currently difficult to monitor job completion for clients calling the REST API. A webhook should be implemented in the master API that can notify users of any changes to job status.
Moreover both the master and worker springboot APIs currently lack any unit testing. Althogh a complete systems test can be ran using the provided sample job. 

## Notes
The docker images created for this project are hosted on Docker Hub: [Master API](https://hub.docker.com/repository/docker/prokopchukdim/dccf-master/general), [Worker Service](https://hub.docker.com/repository/docker/prokopchukdim/dccf-worker/general), [Kafka Kraft](https://hub.docker.com/repository/docker/prokopchukdim/kafka-kraft/general)

