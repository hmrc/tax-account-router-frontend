#!/bin/bash

mongourl=localhost:27017

collection1=$1
collection2=$2

if [ -z "$collection1" ] || [ -z "$collection2" ]
  then echo "Two collection names required. Terminating..."
   exit 1
fi

mongo $mongourl/sa --eval "db.$collection1.createIndex({RET_TAXPAYER_REFERENCE: 1}, {name: 'sa_utr_idx'});"
mongo $mongourl/sa --eval "db.$collection2.createIndex({RET_TAXPAYER_REFERENCE: 1}, {name: 'sa_utr_idx'});"

echo Collections created.