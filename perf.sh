#!/bin/bash

usage() {
    cmd=$(basename "$0")
    echo "usage: $cmd <lang> <numclients> <sleep> <address> <port> <timeout>" >&2
    echo >&2
    echo "         lang:    c, java, python2, python3" >&2
    echo "   numclients:    number of clients to spawn" >&2
    echo "        sleep:    delay between spawning clients" >&2
    echo "      address:    ip address" >&2
    echo "         port:    server port querying to" >&2
    echo "         timeout:    length of timeout" >&2
    echo >&2
    echo "   ex: $cmd java 5 1 127.0.0.1 5300 5" >&2
    echo "       - looks for a java executable client" >&2
    echo "       - spawns 5 instances, 1 second apart" >&2
    echo "       - passes each \"127.0.0.1 5300 5\"" >&2
    echo "         as command-line arguments" >&2
    exit 1
}

if [ "$#" -lt 6 ]
then
    usage
fi

lang="$1"
shift
numclients="$1"
shift
delay="$1"
shift
address="$1"
shift
port="$1"
shift
timeout="$1"

case "$lang" in
    "c")
        if [ ! -x "client" ] 
        then
            echo "error: no 'client' executable found, has it been compiled?"
            exit 1
        fi
        client="./client"
        ;;
    "java")
        if [ ! -f "Client.class" ] 
        then
            echo "error: no 'Client.class' found, has it been compiled?"
            exit 1
        fi
        client="java Client"
        ;;
    "python2")
        if [ ! -f "client.py" ] 
        then
            echo "error: no 'client.py' found"
            exit 1
        fi
        client="python client.py"
        ;;
    "python3")
        if [ ! -f "client.py" ] 
        then
            echo "error: no 'client.py' found"
            exit 1
        fi
        client="python3 client.py"
        ;;
    *)
        echo "error: unrecognised language: ${lang}\n" >&2
        usage
        ;;
esac


pids=
echo "\nspawning clients...\n"

while IFS= read -r line; do
    domain_name=$(echo "$line" | grep -oE '[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}')
    echo "$domain_name"
    eval "$client $address $port $domain_name $timeout" > /dev/null &
    pids="$pids $!"
    sleep "$delay"
    
done < "domains.txt"