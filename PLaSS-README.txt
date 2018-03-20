SB: Here are brief instructions/examples on how to build and execute RoadRunner.

Current status:
Benchmarks that work: hsqldb6, lusearch6, lusearch9, avrora9
Benchmarks that do not work: xalan6, xalan9, luindex9, pmd9, sunflow9, pjbb2000, pjbb2005
Did not try: eclipse6

rrrun -classpath=<path-jar> -maxTid=420 -array=FINE -field=FINE -noxml -quiet -tool=FT Harness hsqldb -s large

