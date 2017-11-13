#!/bin/bash
 
set -x
 
###################
# READ PARAMETERS #
###################
# Validate that all arguments are supplied
if [ $# -lt 5 ]
then
    echo "Insufficient parameters supplied."
    echo "Provided only: $*"
    echo "Exiting."
    exit 1
fi
 
NODE_TYPE=$1 # in use CORDA_LEGAL_NAME, CORDA_NOTARY
AZUREUSER=$2 # in use in config files
NODE_NAME=$3 # in use in CORDA_LEGAL_NAME
NOTARY_TYPE=$4 # in use in CORDA_NOTARY
NETWORKMAP_ADDRESS=$5 # in use in CORDA_NETWORKMAP
WORKING_DIRECTORY="/app/corda"
 
if [ ! -d $WORKING_DIRECTORY ]; then
  sudo mkdir -p /app/corda
fi
 
#############################
# CONFIGURE CORDA VARIABLES #
#############################
 
# CORDA_HOST is IP/hostname visiable to other machine - in private network it can be private IP
CORDA_HOST=$(hostname -i)
# CORDA PORT can be 10002 by default
CORDA_PORT="10002"
# City - we need to get it from user - or just assign randomly
CORDA_CITY="Singapore"
# Country as above
CORDA_COUNTRY="SG"
# Legal Name for Network Map
CORDA_NETWORKMAP_LEGAL_NAME="Network Map"
# Node Legal Name
case  $NODE_TYPE in
    "notary")
        CORDA_LEGAL_NAME="Notary"
        ;;
    "networkmap")
        CORDA_LEGAL_NAME="Network Map"
        ;;
    "node")
        CORDA_LEGAL_NAME=$NODE_NAME
esac
# Notary Type (only for Notary)
if [ $NODE_TYPE == "notary" ]
then
    if [ $NOTARY_TYPE == "validating" ]
    then
        CORDA_NOTARY="corda.notary.validating"
    elif [ $NOTARY_TYPE == "nonValidating" ]
    then
        CORDA_NOTARY="corda.notary.simple"
    fi
else
    CORDA_NOTARY=""
fi
# Network Map Address and Legal Name
# a Corda node becomes Network Map if below config is not present
if [ $NODE_TYPE == "networkmap" ]
then
    CORDA_NETWORKMAP=""
else
    CORDA_NETWORKMAP=$(cat <<EOM
networkMapService : {
    address="$NETWORKMAP_ADDRESS:10002"
    legalName="O=$CORDA_NETWORKMAP_LEGAL_NAME, L=$CORDA_CITY, C=$CORDA_COUNTRY"
}
EOM
  )
fi
#############################
#  INSTALL REQUIRE SOFTWARE #
#############################
# Install ntp and OpenJDK from zulu.org
apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 0x219BD9C9
echo "deb http://repos.azulsystems.com/ubuntu stable main" >> /etc/apt/sources.list.d/zulu.list
apt-get -y update
apt-get -qqy install zulu-8 ntp
 
# Cleanup Optional
#apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
 
# Create /app/corda directory
mkdir -p /app/corda/logs
mkdir -p /app/corda/plugins
 
# Copy corda jar (for now use local dir rather then remote location)
#wget https://dl.bintray.com/r3/corda/net/corda/corda/${VERSION}/corda-${VERSION}.jar -O /app/corda/corda.jar
#wget https://dl.bintray.com/r3/corda/net/corda/corda-webserver/${VERSION}/corda-webserver-${VERSION}.jar -O /app/corda/corda-webserver.jar
 
########################
# Create configuration #
########################
 
# Corda configuration
 
cat > /app/corda/node.conf << EOF
basedir : "/app/corda"
p2pAddress : "$CORDA_HOST:$CORDA_PORT"
rpcAddress : "$CORDA_HOST:10003"
webAddress : "0.0.0.0:10004"
h2port : 11000
myLegalName : "O=$CORDA_LEGAL_NAME, L=$CORDA_CITY, C=$CORDA_COUNTRY"
keyStorePassword : "cordacadevpass"
trustStorePassword : "trustpass"
extraAdvertisedServiceIds: [ "$CORDA_NOTARY" ]
useHTTPS : false
devMode : true
rpcUsers=[
    {
        user=corda
        password=not_blockchain
        permissions=[
            StartFlow.net.corda.flows.CashIssueFlow,
            StartFlow.net.corda.flows.CashExitFlow,
            StartFlow.net.corda.flows.CashPaymentFlow
        ]
    }
]
$CORDA_NETWORKMAP
EOF
 
# Configure SystemD for Corda
cat > /etc/systemd/system/corda.service <<EOF
[Unit]
Description=corda node
Requires=network.target
 
[Service]
Type=simple
User=$AZUREUSER
WorkingDirectory=$WORKING_DIRECTORY
ExecStart=/usr/bin/java -Xmx2048m -jar /app/corda/corda.jar
Restart=on-failure
 
[Install]
WantedBy=multi-user.target
EOF
 
# Configure SystemD for Corda Webserver
cat > /etc/systemd/system/corda-webserver.service <<EOF
[Unit]
Description=Corda Webserver
Requires=network.target
 
[Service]
Type=Simple
User=$AZUREUSER
WorkingDirectory=$WORKING_DIRECTORY
ExecStart=/usr/bin/java -jar /app/corda/corda-webserver.jar
Restart=on-failure
 
[Install]
WantedBy=multi-user.target
EOF
 
###############
# Start Corda #
###############
chown -R $AZUREUSER:$AZUREUSER /app/corda
 
systemctl daemon-reload
systemctl start corda
systemctl start corda-webserver
  