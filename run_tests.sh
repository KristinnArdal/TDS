#!/bin/bash
VERSION=7
NODES=64
CRASH=0
MESSAGES=1600
NUM_TESTS=$1
if [ "$NUM_TESTS" = "" ];
then
	NUM_TESTS=1
fi
if [ ! -d "results/$VERSION/$NODES/" ]; then
	mkdir -p "results/$VERSION/$NODES/" ]
fi
for VERSION in 3 5 7;
do
	for CRASH in {0,2,4,8,16,32}
	do
		echo "Algorithm " $VERSION " with " $CRASH " crashes"
		for i in `seq 1 $NUM_TESTS`;
		do
			echo "Running test" $i "of" $1
			java -jar TDS-0.1.jar -ver $VERSION -v -l 3 -n $NODES -c $CRASH -m $MESSAGES -w 200000 > results/$VERSION/$NODES/m$MESSAGES-c$CRASH-r$i.data
			sleep 1
		done 
	done
done
echo 'Done running' $NUM_TESTS 'tests!'
