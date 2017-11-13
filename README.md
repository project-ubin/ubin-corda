# Project Ubin Phase 2 - Corda

This repository contains the source code and test scripts for the Corda prototype in Project Ubin Phase 2.

Ubin Phase 2 is a collaborative design and rapid prototyping project, exploring the use of Distributed Ledger Technologies (DLT) for Real-Time Gross Settlement. 
* Read the **Project Ubin Phase 2 Report** [here](http://bit.ly/ubin2017rpt).
* For more detailed documentation, refer to the Technical Reports: [Overview](https://github.com/project-ubin/ubin-docs/blob/master/UbinPhase2-Overview.pdf), [Corda](https://github.com/project-ubin/ubin-docs/blob/master/UbinPhase2-Corda.pdf) and [Testing](https://github.com/project-ubin/ubin-docs/blob/master/UbinPhase2-Testing.pdf).

All CorDapp code is in Kotlin and uses custom Corda libraries which are based on Corda v1.0 with additonal fixes for Project Ubin use cases. The libraries are hosted in the following artifactory:
https://ci-artifactory.corda.r3cev.com/artifactory/ubin

A copy of the libraries are also stored in the repository below:
https://github.com/project-ubin/ubin-corda-core.git

Additional notes:
* An external service (mock RTGS service) is to be deployed for Pledge and Redeem functions. It can be found in the [`ubin-ext-service`](https://github.com/project-ubin/ubin-ext-service)
* A common UI can be found in the [`ubin-ui`](https://github.com/project-ubin/ubin-ui) repository

## A. Pre-Requisites

You will need the following installed on your machine before you can start:

* [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
  installed and available on your path.
* Latest version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/)
  (note the community edition is free)
* [h2 web console](http://www.h2database.com/html/download.html)
  (download the "platform-independent zip")
* Git


For more detailed information, see the
[getting set up](https://docs.corda.net/getting-set-up.html) page on the
Corda docsite.

## B. Getting Started

To get started, clone this repository with:
```sh
git clone https://github.com/project-ubin/ubin-corda.git
```

## C. Build CorDapp
1\. Go to newly created folder

```sh
cd ubin-corda
```

2\. Build CorDapp with Gradle

```sh
$ ./gradlew clean build deployNodes
```

3\. CorDapp can be found at:
```sh
ubin-corda/build/nodes/<anyofthefolder>/plugins/

# Example:
ubin-corda/build/nodes/MASRegulator/plugins/
```

## D. Running the network locally:
```sh
$ cd build/nodes
$ ./runnodes
```
All the nodes (defined in the deployNodes gradle task in the project root) will start in
console. From this point the CorDapp can be controlled via web APIs or websites hosted
by the nodes.

# Set Up New Network

Note: Following steps have been tested in Ubuntu 16.04 LTS

## A. Pre-Requisites
You will need the following components set up/installed:

* 15 Ubuntu (Xenial - LTS 16.04) VMs (11 banks, 1 MAS Central Bank node, 1 MAS as Regulator node, 1 Network Map, 1 Notary) with minimum specifications of 1 core, 3.5 GB RAM
* [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
  installed and available on your path.
* Git

## B. Network setup step:
1. Set up network map
2. Set up notary
3. Set up bank nodes
4. Set up Ubin external service (MEPS+ mock service)

The script `configure.sh` from `ubin-corda-deployment` repository takes in 5 input parameters:
* Node type (value: networkmap, notary, node)
* Virtual machine (VM) username
* Network Map Name
* Notary type (default value is "nonValidating")
* Network Map IP address

### 1. Set Up Network Map

1\. SSH to Network Map virtual machine.

2\. Clone `ubin-corda-deployment` repository
```sh
$ git clone https://github.com/project-ubin/ubin-corda-deployment.git
```
3\. Determine network map node name (e.q. Network Map)

4\. Get virtual machine IP address with following command:
```sh
$ hostname -i
```
5\. Execute `configure.sh` script with:
```sh
sudo ./configure.sh networkmap <<VM Username>> <<Network Map Name>> nonValidating <<Network Map IP>>
```
   Example:

```sh
# Network map name is "Network Map"
# Network map IP address is "10.0.0.47"
# VM username is "azureuser"

chmod +x configure.sh
sudo ./configure.sh networkmap azureuser "Network Map" nonValidating 10.0.0.47
```

Note: Network map IP address is required in the set up of notary node and additional bank nodes

### 2. Set Up Notary

1\. SSH to Notary virtual machine.

2\. Clone `ubin-corda-deployment` repository

```sh
$ git clone https://github.com/project-ubin/ubin-corda-deployment.git
```

3\. Determine Notary node name (e.g. Notary)

4\. Get network map IP Address from the previous step (network map setup).

5\.  Execute `configure.sh` script with:
```sh
sudo ./configure.sh networkmap <<VM Username>> <<Network Map Name>> nonValidating <<Network Map IP>>>
```

   Example:
```sh
# Notary name is "Notary"
# Network map IP address is "10.0.0.47"
# VM username is "azureuser"
$ chmod +x configure.sh
$ sudo ./configure.sh notary azureuser "Notary" nonValidating 10.0.0.47
```

### 3. Set Up Bank Nodes

1\. SSH to bank nodes virtual machine.

2\. Clone `ubin-corda-deployment` repository

```sh
$ git clone https://github.com/project-ubin/ubin-corda-deployment.git
```

3\. Determine bank node name (usually the bank SWIFT code).

4\. Get network map IP Address from the previous step (network map setup).

5\. Go to `ubin-corda-deployment` directory.

6\. Verify `config.properties` to ensure `ApproveRedeemURI` is configured with the Central Bank domain name.
```sh
ApproveRedeemURI=http://<<centralbankdomain>>:9001/meps/redeem
```

7\. Execute `configure.sh` script with:

```sh
$ sudo ./configure.sh notary <<VM Username>> <<Notary Name>> nonValidating <<Network Map IP>>
```
   Example:
```sh
# Nodename name is "BankA"
# Network map IP address is "10.0.0.47"
# VM username is "azureuser"

$ chmod +x configure.sh
$ sudo ./configure.sh node azureuser "BankA" nonValidating 10.0.0.47
```
Note: do not name the Corda node with a name containing "node".


### 4. Setup Ubin External Service

Ubin external service should be set up in the `Central Bank` virtual machine. This is a mock service of the current RTGS system, MEPS+. 

#### Build

1\. Clone the repository locally

```sh
$ git clone https://github.com/project-ubin/ubin-ext-service.git
```
2\. Go to newly created folder

```sh
$ cd ubin-ext-service
```

3\. Build project using gradle

```sh
$ ./gradlew build
```
4\. Build artifact can be found at

    build/libs/ubin-ext-service-0.0.1-SNAPSHOT.jar


#### Start External Service
1\. Update the `application.properties` file
```sh
ubin-ext-service/application.properties
```
With Corda configurations:

```sh
PledgeURI=http://cordaknqx-node1.southeastasia.cloudapp.azure.com:9001/api/fund/pledge
RedeemURI=http://cordaknqx-node1.southeastasia.cloudapp.azure.com:9001/api/fund/redeem
Dlt=Corda
```

Note:

- `cordaknqx-node1.southeastasia.cloudapp.azure.com` is the Central Bank domain name in the current network.
- `RedeemURI` is not used in Corda, it is just a placeholder.

2\. Copy built JAR artifact and properties files to the Central Bank VM
```sh
ubin-ext-service/build/libs/ubin-ext-service-0.0.1-SNAPSHOT.jar
ubin-ext-service/application.properties
```
Note: Ensure both files are in the same directory

3\. From Central Bank VM, start the mock service application
```sh
$ java -jar -Dspring.config.location=application.properties -Dserver.port=9001 ubin-ext-service-0.0.1-SNAPSHOT.jar
```
# Central Deployment and Server Admin

## A. Getting Started
1\. Go to Node 0 Virtual Machine
2\. From the home directory, clone the `ubin-corda-deployment` repository
```sh
$ git clone https://github.com/project-ubin/ubin-corda-deployment.git
```

## B. CorDapp Deployment

1\. Copy CorDapp JARs into VM Node 0 into the following directory with SCP/FTP:
```sh
/home/azureuser/ubin-corda-deployment/plugin
```
2\. Log in to VM Node 0 using SSH
3\. Go to `ubin-corda-deployment` directory

4\. Execute manage.sh to deploy CorDapp to all nodes in the network except the Notary and the Network Map. The script assumes that the nodes are named sequentially (e.g. 0 to 12):
```sh
$ ./manage.sh deploy 0 12
```

This step does the following:

- Delete everything in /app/corda/plugins
- Copy all files from Node 0's /home/azureuser/deploy to the target node's /app/corda/plugins folder
- Restart Corda and webserver in the node
- Repeat for selected Nodes

Note: 0 and 12 in Step 4 represents the range of nodes. If you only require deployment on nodes 2-4, change the parameters to "deploy 2 4".

## C. Restart All Nodes
1\. Log in to VM Node 0 using SSH
2\. Go to `ubin-corda-deployment` directory
3\. Execute `manage.sh` to restart all nodes in the network (e.g. Node 0 - Node 12):
```sh
$ ./manage.sh restart 0 12
```
Note: 0 and 12 in Step 3 represents the range of nodes. If you only require deployment on nodes 2-4, change the parameters to "deploy 2 4".

## D. Stop All Nodes
1\. Log in to VM Node 0 using SSH
2\. Go to `ubin-corda-deployment` directory
3\. Execute `manage.sh` to stop all nodes in the network (e.g. Node 0 - Node 12):
```sh
$ ./manage.sh stop 0 12
```
Note: 0 and 12 in Step 3 represents the range of nodes. If you only require deployment on nodes 2-4, change the parameters to "deploy 2 4".

## E. Clear All Data in Vault
1\. Log in to VM Node 0 using SSH
2\. Check that you are in `/home/azureuser`
3\. Go to `ubin-corda-deployment`
4\. Stop all Corda nodes (node 0 to 12) using:
```sh
$ ./manage.sh stop 0 12
```

5\. Clear all data in network (node 0 to 12) - note that the data is *not recoverable*:

```sh
$ ./manage.sh reset 0 12
```

6\. Restart all nodes 0 to 12:

```sh
$ ./manage.sh restart 0 12
```

## F. Update Node Names
1\. Stop the Corda server and webserver
2\. Go to `/app/corda`
3\. Update `node.conf` with command:
```sh
$ vi node.conf
```
4\. Change `myLegalName` in "O" key :
```sh
"O=BOTKSGSX, L=Singapore, C=Singapore"
```
5\. Delete the `certificates` folder. The certificates will be regenerated automatically upon Corda server start up
6\. To purge the old entries in the Network Map, login to the h2 DB of the Network Map (nm0) and delete the old entries in `NODE_NETWORK_MAP_NODES` table

Note:

As of Corda v1.0, 'CN' / Common Name field in the X500 is no longer supported. It is used only for services identity. In addition, words such as "node" is blacklisted in CordaX500Name and therefore should not be used.

## G. Database Admin

(Based on instructions in https://docs.corda.net/node-database.html)

1\. Install h2 client locally and run h2.sh (Unix) or h2.bat (Windows)
2\. Enter the JDBC URL with format:
```
jdbc:h2:tcp://<<CORDA NODE HOST>>:<<H2 DATABASE PORT>>/node

# Example:
jdbc:h2:tcp://cordaknqx-node0.southeastasia.cloudapp.azure.com:11000/node
```
3\. Username and password is as per default
4\. Connect to browse h2 DB

## H. Individual Node CorDapp Deployment

1\. Copy new/updated CorDapp JARs to the following directory in the Corda Node VM:
```sh
/app/corda/plugins
```
2\. Restart Corda nodes:
```sh
$ sudo systemctl restart corda
$ sudo systemctl restart corda-webserver
```

Note:
- Replace "restart" with "start" or "stop" to simply start/stop the services
- Replace "restart" with "status" to view the latest logs
- Log location: `/app/corda/logs`
- Once the CorDapp JARs are uploaded and replaced, the Corda server (service) must be restarted


# Test Scripts

[Postman](https://www.getpostman.com/) is the main testing tool used for this prototype. The Postman collection and environments are located in the [test-scripts](test-scripts) folder in this repository. The API definitions can be found in the [Technical Report repository](https://github.com/project-ubin/ubin-docs/blob/master/api/UbinPhase2-CordaAPI.pdf).

# License

Copyright 2017 The Association of Banks in Singapore

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
