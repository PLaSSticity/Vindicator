#!/bin/bash

for BENCH in avrora eclipse jython pmd tomcat xalan
do
  THREADS=8
  if [ "$BENCH" = "tomcat" ]
  then
    THREADS=4
  fi
  ln -s -f $BENCH-small.txt latest.txt
  timeout $1 \
    rrrun -classpath=/home/mikebond/benchmarks/dacapo-9.12-bach.jar \
          -tool=WDC -noTidGC -noxml \
          Harness -t $THREADS -s small $BENCH \
          >& $BENCH-small.txt
done
