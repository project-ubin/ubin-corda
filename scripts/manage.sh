#!/bin/bash

echo "Mode: $1"
echo "Range of nodes: $2 to $3"
echo "--------------"

username='username'
host_prefix='cordavm'
host_suffix='.example.com'
notary_host='notary.example.com'
networkmap_host='networkmap.example.com'
nm_db_url='jdbc:h2:tcp://networkmap.example.com:11000/node'
nm_db_user='sa'

networkmap=$username'@'$networkmap_host

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]
then
  echo "  Please specify 'deploy' or 'restart'"
  echo "  ---Example to deploy Nodes 2 and 6---"
  echo "  ./manage.sh deploy 2 6"
  echo "Exiting.."
  exit
fi
if [ "$1" = "restart" ]
then
  for (( i=$2; i<=$3; i++ ))
    do
      node=$username'@'$host_prefix$i$host_suffix
      ssh $node sudo systemctl restart corda
      ssh $node sudo systemctl restart corda-webserver
      echo "Restarted node "$i
    done
elif [ "$1" = "stop" ]
then
  for (( i=$2; i<=$3; i++ ))
    do
      node=$username'@'$host_prefix'$i'host_prefix
      ssh $node sudo systemctl stop corda
      ssh $node sudo systemctl stop corda-webserver
      echo "Stopped node "$i
    done
elif [ "$1" = "deploy" ]
then
  for (( i=$2; i<=$3; i++ ))
    do
      node=$username'@'$host_prefix$i$host_suffix
      echo "Deleting CorDapps in plugin folder in Node $i"
      ssh $node sudo rm /app/corda/plugins/*.jar
      echo "Copying files to Node $i"
      scp -r deploy/* $node:/app/corda/plugins/
      echo "Restarting Node $i Corda and Webserver..."
      ssh $node sudo systemctl restart corda
      ssh $node sudo systemctl restart corda-webserver
      echo "Node $i ready"
    done
elif [ "$1" = "deployhard" ]
then
	notary=$username'@'$notary_host
	echo "Stopping Notary Node Corda and Webserver..."
	ssh $notary sudo systemctl stop corda
	ssh $notary sudo systemctl stop corda-webserver
	echo "Deleting DB file"
	ssh $notary rm /app/corda/persistence.mv.db
	ssh $notary rm -rf /app/corda/artemis
	echo "Deleting certificates"
    ssh $notary rm /app/corda/certificates/*.jks

	for (( i=$2; i<=$3; i++ ))
		do
		  node=$username'@'$host_prefix$i$host_suffix
		  echo "Stppping Node $i Corda and Webserver..."
		  ssh $node sudo systemctl stop corda
		  ssh $node sudo systemctl stop corda-webserver
		  echo "Deleting DB file"
		  ssh $node sudo rm /app/corda/persistence.mv.db
		  ssh $node sudo rm -rf /app/corda/artemis
		  echo "Deleting certificates"
		  ssh $node sudo rm /app/corda/certificates/*.jks
		  echo "Deleting CorDapps in plugin folder in Node $i"
		  ssh $node sudo rm /app/corda/plugins/*.jar
		  echo "Copying files to Node $i"
		  scp -r deploy/* $node:/app/corda/plugins/
		done

	echo "Clearing Networkmap Table"

	java -cp h2/h2.jar org.h2.tools.RunScript -url $nm_db_url -user $nm_db_user -script truncate.sql -showResults


	echo "Restarting Networkmap Node"
	ssh $networkmap sudo systemctl restart corda
	ssh $networkmap sudo systemctl restart corda-webserver
	echo "Restarting Notary Node"
	ssh $notary sudo systemctl restart corda
	ssh $notary sudo systemctl restart corda-webserver
elif [ "$1" = "resetmap" ]
then
	echo "Clearing Networkmap Table"
	java -cp h2/h2.jar org.h2.tools.RunScript -url $nm_db_url -user $nm_db_user -script truncate.sql -showResults
	echo "Restarting Networkmap Node"
	ssh $networkmap sudo systemctl restart corda
	ssh $networkmap sudo systemctl restart corda-webserver
elif [ "$1" = "reset" ]
then
  for (( i=$2; i<=$3; i++ ))
    do
      node=$username'@'$host_prefix'$i'host_prefix
      echo "Shutting down Corda in Node $i"
      ssh $node sudo systemctl stop corda
      echo "Deleting DB file"
      ssh $node rm /app/corda/persistence.mv.db
      ssh $node rm -rf /app/corda/artemis
   done
elif [ "$1" = "rename" ]
then
  for (( i=$2; i<=$3; i++ ))
    do
      node=$username'@'$host_prefix'$i'host_prefix
      echo "Shutting down Corda in Node $i"
      ssh $node sudo systemctl stop corda
      echo "Deleting certificates"
      ssh $node rm -rf /app/corda/certificates
    done
elif [ "$1" = "clearlog" ]
then
  for (( i=$2; i<=$3; i++ ))
    do
      node=$username'@'$host_prefix'$i'host_prefix
      echo "Shutting down Corda in Node $i"
      ssh $node sudo systemctl stop corda
      echo "Deleting logs"
      ssh $node rm -rf /app/corda/logs/*.log
	  ssh $node rm -rf /app/corda/logs/web/*.log
    done
else
  echo "Please specify 'deploy', 'restart', 'stop' or 'reset'"
  echo "---Example to deploy Nodes 2 and 6---"
  echo "./manage.sh deploy 2 6"
  echo "Exiting.."
fi
