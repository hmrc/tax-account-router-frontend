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

mongo $mongourl/sa --eval "
var count = 0;
db.$collectionName.find({RET_TAXPAYER_REFERENCE: {\$exists: true}}).forEach(function(x) {
    x.RET_TAXPAYER_REFERENCE = '' + x.RET_TAXPAYER_REFERENCE;
    db.$collectionName.save(x);
    count = count + 1;
    print('RET_TAXPAYER_REFERENCE converted to String for ' + count + ' documents.');
});
'RET_TAXPAYER_REFERENCE converted to String for ' + count + ' documents.';"
