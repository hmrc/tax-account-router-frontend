#!/bin/bash

fileName=$1
fileNumber=$2

if [ -z "$fileName" ]
  then echo "File name required."
   exit 1
fi

if [ -z "$fileNumber" ]
  then echo "File number required."
   exit 1
fi

awk 'BEGIN{i='$fileNumber'000000000;FS=OFS=","} $1 ~ /^(([0-9]{1,9}K)|([0-9]{1,10}))$/{$1=++i} 1' $fileName
