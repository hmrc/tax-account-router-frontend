#!/bin/bash

mongourl=localhost:27017

collectionName=$1

if [ -z "$collectionName" ]
  then echo "Collection name required."
   exit 1
fi

mongo $mongourl/sa --eval "
var count = 0;
db.$collectionName.find({RET_TAXPAYER_REFERENCE: {\$type: 16}}).forEach(function(x) {
    x.RET_TAXPAYER_REFERENCE = '' + x.RET_TAXPAYER_REFERENCE;
    db.$collectionName.save(x);
    count = count + 1;
    if (count % 10000 == 0) print((count / 1000) + 'K processed');
});
'RET_TAXPAYER_REFERENCE converted to String for ' + count + ' documents.';"

