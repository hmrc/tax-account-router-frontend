#!/bin/bash

mongourl=localhost:27017

collectionName=$1
fileName=$2

if [ -z "$collectionName" ]
  then echo "Collection name required."
   exit 1
fi
if [ -z "$fileName" ]
  then echo "File name required."
   exit 1
fi

tail -n 3 "$fileName" | wc -c | xargs -I {} truncate "$fileName" -s -{}

mongoimport --host $mongourl --db sa --collection $collectionName --ignoreBlanks --type csv --headerline $fileName
