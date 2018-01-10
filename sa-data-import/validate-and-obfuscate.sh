#!/bin/bash

fileName=$1
fileNumber=$2
obfuscatedFileName=$3

if [ -z "$fileName" ]
  then echo "File name required."
   exit 1
fi

if [ -z "$fileNumber" ]
  then echo "File number required."
   exit 1
fi

if [ -z "$obfuscatedFileName" ]
  then echo "Obfuscated file name required."
   exit 1
fi

# The last three rows should be the totals.
tail -n 3 "$fileName" | awk 'BEGIN{FS=OFS=","} NF>1{print "The last three rows should be the file summary."; exit}'

# There should be 19 columns in each row
linesNumber=`awk 'END {print NR}' "$fileName"`
dataLines=$((linesNumber-3))
awk 'BEGIN{FS=OFS=","} NF!=19 && NR <= '$dataLines'{print NF " columns found in line " NR ". Expected 19."; exit}' $fileName

awk 'BEGIN{i='$fileNumber'000000000;FS=OFS=","} $1 ~ /^(([0-9]{1,9}K)|([0-9]{1,10}))$/{$1=++i} 1' $fileName > $obfuscatedFileName
