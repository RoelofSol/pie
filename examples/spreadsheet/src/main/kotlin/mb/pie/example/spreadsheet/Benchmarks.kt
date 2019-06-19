package mb.pie.example.spreadsheet

import mb.fs.api.node.FSNode
import mb.pie.api.None
import mb.pie.api.Pie
import mb.pie.api.fs.FileSystemResource
import mb.pie.runtime.PieBuilderImpl
import mb.pie.runtime.logger.StreamLogger
import mb.pie.runtime.taskdefs.MutableMapTaskDefs
import java.io.FileWriter
import java.lang.ref.WeakReference

val RESULT_DiR = "/home/rs/thesis/bench/results/"

private fun gc() {
    var obj: Any? = Any()
    val ref = WeakReference<Any>(obj)
    //noinspection AssignmentToNull,UnusedAssignment
    obj = null
    do {
        System.gc()
    } while (ref.get() != null)
}

abstract class BenchGraph {
    abstract val pie : Pie;
    abstract val changedFiles: Set<FSNode>
    abstract fun setSize(i: Int);
    fun changedResource() =  changedFiles.map { FileSystemResource(it).key() }.toSet()
}

class TubeGraph : BenchGraph () {
    override val pie : Pie;
    override val changedFiles : Set<FSNode> = setOf( TubeTop.ShapeTrigger , TubeBottom.ResultTrigger);
    override fun setSize(size: Int) {
        TubeTop.Shape = size;
        TubeBottom.Result =  size;
    }

    val top = TubeTop();
    val topkey = top.createTask(None());
    init {
        val map = MutableMapTaskDefs()

        map.add(top.id, top);
        map.add(TubeBottom().id, TubeBottom())
        map.add(TubeEdge().id, TubeEdge())

        val pieBuilder = PieBuilderImpl()
        pieBuilder.withTaskDefs(map)
        //pieBuilder.withLogger(StreamLogger.verbose()  );
        pie = pieBuilder.build();
        println("BUILDING NEW PIE");
        pie.bottomUpObservableExecutor.requireTopDown(topkey);

    }
}




class WideDiamondGraph : BenchGraph() {
    override val pie : Pie;
    override val changedFiles : Set<FSNode> = setOf( DiamondTop.ShapeTrigger , DiamondBottom.ResultTrigger);
    override fun setSize(size: Int) {
        DiamondTop.Shape = (0..size).toList()
        DiamondBottom.Result = 100+(Math.random()*100.0).toInt()
    }
    val bt = DiamondTop();
    init {
        val map = MutableMapTaskDefs()
        val dt = DiamondTop()
        map.add(dt.id, dt);
        map.add(DiamondBottom().id, DiamondBottom())
        map.add(DiamondEdge().id, DiamondEdge())

        val pieBuilder = PieBuilderImpl()
        pieBuilder.withTaskDefs(map)
       // pieBuilder.withLogger(StreamLogger.verbose()  );
        pie = pieBuilder.build();

        println("BUILDING NEW PIE");
        pie.bottomUpObservableExecutor.requireTopDown(dt.createTask(None()));
    }
}

fun test_bench() {

    val pie = TubeGraph();

    val exec = pie.pie.bottomUpExecutor;
    pie.setSize(5)

    exec.requireBottomUp(pie.changedResource())

    pie.setSize(10);

    exec.requireBottomUp(pie.changedResource())

    pie.setSize(5);
    exec.requireBottomUp(pie.changedResource())
    pie.setSize(2);

    exec.requireBottomUp(pie.changedResource())
}



fun write_tube_csv(file : String , data: List<List<BenchResult>>,steps:List<Int>) {

    var noexec = FileWriter(file+"_noexec.csv");
    var one_exec = FileWriter(file+"_once.csv");
    var twice_exec = FileWriter(file+"_twice.csv");

    noexec.appendln(steps.joinToString(","))
    one_exec.appendln(steps.joinToString(","))
    twice_exec.appendln(steps.joinToString(","))
    for (trial in data) {
        noexec.appendln(trial.map{ it.noExec}.joinToString(","))
        one_exec.appendln(trial.map{ it.execOnce}.joinToString(","))
        twice_exec.appendln(trial.map{ it.execTwice}.joinToString(","))

    }
    noexec.flush()
    one_exec.flush()
    twice_exec.flush()
    noexec.close()
    one_exec.close()
    twice_exec.close()

}


fun bench_tube() {
    TubeEdge.AddSleep = false;
    // return test_bench()
    val steps = (100..1000 step 100).toList();
    val warmups = (1..15);
    val trials = 1..30;
    write_tube_csv("${RESULT_DiR}tube_comp/obswarmup",warmups.map { obs_tube_trial( TubeGraph(),steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { obs_tube_trial( TubeGraph() ,steps) }.toList();
    write_tube_csv("${RESULT_DiR}tube_comp/tube_obs",results,steps);
    write_tube_csv("${RESULT_DiR}tube_comp/noobswarmup",warmups.map { no_obs_tube_trial( TubeGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { no_obs_tube_trial( TubeGraph(),steps )}.toList();
    write_tube_csv("${RESULT_DiR}tube_comp/tube_no_obs",noresults,steps);
}
fun bench_tube_sleep() {
    TubeEdge.AddSleep = true;
    // return test_bench()
    val steps = (10..100 step 10).toList();
    val warmups = (1..2);
    val trials = 1..5;
    write_tube_csv("${RESULT_DiR}tube_sleep/obswarmup",warmups.map { obs_tube_trial( TubeGraph(),steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { obs_tube_trial( TubeGraph() ,steps) }.toList();
    write_tube_csv("${RESULT_DiR}tube_sleep/tube_obs",results,steps);
    write_tube_csv("${RESULT_DiR}tube_sleep/noobswarmup",warmups.map { no_obs_tube_trial( TubeGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { no_obs_tube_trial( TubeGraph(),steps )}.toList();
    write_tube_csv("${RESULT_DiR}tube_sleep/tube_no_obs",noresults,steps);
}





data class BenchResult(val noExec : Long, val execOnce : Long, val execTwice : Long )

fun obs_tube_trial(graph : TubeGraph, steps: List<Int>): List<BenchResult> {
    gc()
    val changes = graph.changedResource();
    val trace = mutableListOf<Int>()
    graph.pie.bottomUpObservableExecutor.setObserver(graph.topkey.key()) {
        trace.add(it as Int)
    };
    val results =  steps.map{

      //  println("Refresh")
        graph.setSize(it);
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);

        println("TRIAL: ReObs")
        val startTime = System.nanoTime()
        graph.pie.bottomUpObservableExecutor.dropRootObserved(graph.topkey.key());
        graph.pie.bottomUpObservableExecutor.addRootObserved(graph.topkey);
        val a = System.nanoTime() - startTime;

        //println("TRIAL: ReObs Exec (Dropping)")
        val startTime2 = System.nanoTime()
        graph.pie.bottomUpObservableExecutor.dropRootObserved(graph.topkey.key());
        graph.setSize(it+1);
        //println(" Require (Unobs) ")
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);

        //println(" Reobserve  ")
        graph.pie.bottomUpObservableExecutor.addRootObserved(graph.topkey);
        val b = System.nanoTime() - startTime2;

        //println(" TRIAL:ReObs Exec Twice")
        val startTime3 = System.nanoTime()
        graph.pie.bottomUpObservableExecutor.dropRootObserved(graph.topkey.key());
        //println(" Require (Unobs) ")
        graph.setSize(it+2);
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);
        //println(" Require (Unobs) ")
        graph.setSize(it+3);
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);
       // println(" Reobserve  ")
        graph.pie.bottomUpObservableExecutor.addRootObserved(graph.topkey);
        val c = System.nanoTime() - startTime3;
        BenchResult(a,b,c)
    }.toList();
    println("Trace OBS ${trace.size}")
    return results;

}

fun  no_obs_tube_trial(graph : TubeGraph, steps: List<Int>): List<BenchResult> {
    gc()
    val changes = graph.changedResource();
    val trace = mutableListOf<Int>()
    graph.pie.bottomUpExecutor.setObserver(graph.topkey.key()) {

        trace.add(it as Int)
    };
    val results =  steps.map{
      //  println("Refresh")
        graph.setSize(it);
        graph.pie.bottomUpExecutor.requireBottomUp(changes);

        //println("TRIAL: Exec")
        val startTime2 = System.nanoTime()
        //println(" Require (obs) ")
        graph.setSize(it+1);
        graph.pie.bottomUpExecutor.requireBottomUp(changes);
        val b = System.nanoTime() - startTime2;

      //  println(" TRIAL: Exec Twice")
        val startTime3 = System.nanoTime()
        graph.setSize(it+2);
      //  println(" Require (obs) ")
        graph.pie.bottomUpExecutor.requireBottomUp(changes);
        graph.setSize(it+3);
      //  println(" Require (obs) ")
        graph.pie.bottomUpExecutor.requireBottomUp(changes);
        val c = System.nanoTime() - startTime3;

        BenchResult(0,b,c)
    }.toList();
    println("Unobs results ${trace.size}")
    return results
}

fun bench_diamond_sleep() {
    DiamondEdge.AddSleep = true
    val forward = (10..100 step 10);
    val steps = forward + (forward.reversed())
    val warmups = (1..1)
    val trials = 1..3;
    write_csv(".,/results/diamond_sleep/obswarmup",warmups.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList();
    write_csv(".,/results/diamond_sleep/obstrial.csv",results,steps);


    write_csv(".,/results/diamond_sleep/noobswarmup",warmups.map { diamond_no_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { diamond_no_obs_trial( WideDiamondGraph(),steps )}.toList();
    write_csv(".,/results/diamond_sleep/noobstrial.csv",noresults,steps);

}
fun bench_diamond_comp() {
    DiamondEdge.AddSleep = false
    // return test_bench()
    val forward = (100..1000 step 100);
    val steps = forward + (forward.reversed())
    val warmups = (1..5)
    val trials = 1..10;
    write_csv(".,/results/diamond_comp/obswarmup",warmups.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList();
    write_csv(".,/results/diamond_comp/obstrial.csv",results,steps);


    write_csv(".,/results/diamond_comp/noobswarmup",warmups.map { diamond_no_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { diamond_no_obs_trial( WideDiamondGraph(),steps )}.toList();
    write_csv(".,/results/diamond_comp/noobstrial.csv",noresults,steps);

}


fun diamond_obs_trial(graph : WideDiamondGraph, steps: List<Int>): List<Long> {
    gc()
    val changes = graph.changedResource();

    return steps.map{
                graph.setSize(it);
                val startTime = System.nanoTime()
                graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);
        System.nanoTime() - startTime
            };
}
fun diamond_no_obs_trial(graph : WideDiamondGraph,steps: List<Int>): List<Long> {
    gc()
    val changes = graph.changedResource();
    return steps.map{
        graph.setSize(it);
        val startTime = System.nanoTime()
        graph.pie.bottomUpExecutor.requireBottomUp(changes);
        System.nanoTime() - startTime
    };
}

fun bench_all(){
    bench_tube_sleep();
    bench_tube()

    bench_diamond_sleep()
    bench_diamond_comp()
}

fun write_csv(file : String , data: List<List<Long>>,steps:List<Int>) {
    var fileWriter = FileWriter(file);
    fileWriter.appendln(steps.mapIndexed{i,t -> i}.joinToString(","))
    for (trial in data) {
        fileWriter.appendln(trial.joinToString(","));
    }
    fileWriter!!.flush()

    fileWriter.close()
}
