************************************************************
* High-Coverage, Unbounded Sound Predictive Race Detection *
************************************************************

This README provides instructions to build and execute our implementation of Vindicator 
of the PLDI 2018 Submission #95 High-Coverage, Unbounded Sound Predictive Race Detection.

Vindicator detects and verifies hard-to-detect races between accesses that are millions of events apart - outside the range of windowed approaches - and also detects and verifies races that the state-of-the-art unbounded approach (WCP) cannot find.

Refer to the accompanying paper for more details: 
High-Coverage, Unbounded Sound Predictive Race Detection

************************
* Executing Vindicator *
************************

1. '~/Vindicator/src': source code for a modified version of RoadRunner (https://github.com/stephenfreund/RoadRunner/releases/tag/v0.5) implementing Vindicator and configurations Base, HB + WCP (Happens-Before and Weak Causally-Precedes), and Vindicator w/o G (Vindicator without event graph G generation) as described in the paper.

2. '~/Vindicator/benchmarks': benchmark scripts and .jar for DaCapo benchmarks, version 9.12 Bach [1] (http://dacapobench.org/). We use RoadRunner's provided support for harnessing and running the DaCapo programs, the provided workloads are close to DaCapo's default workload size. RoadRunner does not at the time of this release support eclipse, tradebeans, or tradesoap.

To build the tool:
 - $ cd ~/Vindicator
 - $ ant ; source msetup

Set the path for the tool:
 - $ export PATH=$PATH:~/Vindicator/build/bin/

To execute the benchmark scripts provided:
 - $ cd ~/Vindicator/benchmarks/<provided benchmark>
 - $ ./TEST

To execute a benchmark with java directly:
 - $ /usr/lib/jvm/java-1.8.0/bin/java -javaagent:<Vindicator directory>/build/jar/rragent.jar -Xmx120g -Xbootclasspath/p:<Vindicator directory>/classes:<Vindicator directory>/jars/java-cup-11a.jar: rr.RRMain -classpath=<Vindicator directory>/benchmarks/<provided benchmark>/original.jar -maxTid=14 -array=FINE -field=FINE -noTidGC -availableProcessors=4 -tool=WDC -benchmark=1 -warmup=0 RRBench

## Note
Some benchmarks require over 60GB to run with our tool, see paper for details. Using the above command, max heap size is set using -Xmx

Relevant flags for running our tool can be found by running:
  - $ cd ~/raptor ; ant ; source msetup
  - $ rrrun -help	[Detailed under * Running New Experiments *]
An important argument to keep in mind is:
  -availableProcessors=X	[sets X number of processors as available for running application]

A list of configurations to run specific RoadRunner tools are:
  -tool=N -noinst           [Native]
  -tool=N                   [Base]
  -tool=WDC -wdcHBWCPOnly   [HB + WCP]
  -tool=WDC -testConfig     [Vindicator w/o G]
  -tool=WDC                 [Vindicator]

The following are the benchmark names found in the paper with corresponding scripts in the ~/Vindicator/benchmarks directory:
  avrora
  batik
  h2
  jython
  luindex
  lusearch
  pmd
  sunflow
  tomcat
  xalan

***************************
* Running New Experiments *
***************************

## Running Stand-Alone Examples

RoadRunner can build and execute tools on separate examples outside of exp.
To test out smaller examples, execute the following:
  - $ cd ~/Vindicator ; ant ; source msetup
  - $ javac test/Test.java
  - $ rrrun -tool=WDC -noTidGC test.Test

## Examples 1, 2, 3, 4a, and 4b correspond to the Figures from the High-Coverage, Unbounded Sound Predictive Race Detection paper.
## Examples 8, 9a, 9b, and 9c correspond to the Figures from Appendix C of the Extended technical report version of the paper.

The examples in the ~/raptor/test' directory represent the figures in the paper, labeled Figure1 through Figure9c.
  - $ cd ~/raptor ; ant ; source msetup
  - $ javac test/Figure1.java
  - $ rrrun -tool=WDC -noTidGC test.Figure1

## Creating and Running New Examples

The Test.java file under '~/raptor/test' is a template example.
Modifications to the Test.java file for simple examples or moderately complex examples can use the following operations:
  - write(x)   => x = 1; [assuming x is an integer variable]
  - read(x)    => int t = x; [some thread local variable t]
  - acquire(m) => synchronized(m) {
  - release(m) => }
  - sync(o)    => sync(o); is equivalent to executing acquire(o); read(oVar); write(oVar); release(o);
  - sleep(1)   => performs a 1 second sleep to the executing thread. This allows for control over precise event execution order.

For example, the following execution can be written as:
acquire(m)     synchronized(m) {
 write(x)         x = 1;
release(m)     }
sync(o)        sync(0);

To test out the new example, execute the following:
  - $ cd ~/raptor ; ant ; source msetup
  - $ javac test/Test.java
  - $ rrrun -tool=WDC -noTidGC test.Test

Additional options for RoadRunner:
  - $ rrrun -help

All dependencies and packages are already installed, but if an issue arises during building, then it is most likely related to a path or java version issue.

**********************************
* Brief Guide to the Source Code *
**********************************

Our implementation is built in RoadRunner version 0.5. Use Eclipse to view and modify the source code. Please refer to https://github.com/stephenfreund/RoadRunner on how to set up RoadRunner in Eclipse.

Our tool, Vindicator, and related configurations are located under the '~/raptor/src/tools/wdc' directory. Important files are:

1. WDCTool.java file contains the source code implementing the central vector clock analysis (Algorithm 2 in the supplementary material). 
  - The boolean flag HB_WCP_ONLY (WDCTool.java, line 110) enables the HB + WCP configuration. 
    The presence of the flag disables pieces of the Vindicator analysis related to tracking the DC relation to obtain a pure WCP analysis.
  - The boolean flag BUILD_EVENT_GRAPH (WDCTool.java, line 107) enables the DC w/o G configuration. 
    The presence of the flag disables pieces of the Vindicator analysis related to constructing the constraint graph during analysis. 

2. EventNode.java file contains the source code implementing the constraint graph construction and VindicateRace (Algorithm 1 in the paper).

The VERBOSE flag in WDCTool.java and VERBOSE_GRAPH and USE_DEBUGGING_INFO flags in EventNode.java will enable useful assertions and track additional information used during output. As a warning, these flags may cause extreme slowdowns and are mostly used for testing the specific functionality of our tool.

***********
* Contact *
***********

Feel free to contact about with any issues or questions.
Jake Roemer: roemer.37@buckeyemail.osu.edu

**************
* References *
**************

[1] Blackburn, S. M., Garner, R., Hoffman, C., Khan, A. M., McKinley, K. S., Bentzur, R., Diwan, A., Feinberg, D., Frampton, D., Guyer, S. Z., Hirzel, M., Hosking, A., Jump, M., Lee, H., Moss, J. E. B., Phansalkar, A., Stefanovic, D., VanDrunen, T., von Dincklage, D., and Wiedermann, B. The DaCapo Benchmarks: Java Benchmarking Development and Analysis, OOPSLA '06: Proceedings of the 21st annual ACM SIGPLAN conference on Object-Oriented Programing, Systems, Languages, and Applications, (Portland, OR, USA, October 22-26, 2006)
