package mb.pie.example.spreadsheet

import mb.fs.api.node.FSNode
import mb.pie.api.None
import mb.pie.api.Pie
import mb.pie.api.fs.FileSystemResource
import mb.pie.runtime.PieBuilderImpl
import mb.pie.runtime.logger.StreamLogger
import mb.pie.runtime.taskdefs.MutableMapTaskDefs
import org.openjdk.jmh.infra.Blackhole
import java.io.FileWriter
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities

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
    companion object {
        val variant = ""
    }
    abstract val pie : Pie;
    abstract val changedFiles: Set<FSNode>
    abstract fun setSize(i: Int);
    fun changedResource() =  changedFiles.map { FileSystemResource(it).key() }.toSet()
}

class TubeGraph : BenchGraph () {
    override val pie : Pie;
    override val changedFiles : Set<FSNode> = setOf(  TubeBottom.ResultTrigger);
    override fun setSize(size: Int) {
        TubeTop.Shape = size;
    }
    fun log(s: String) {
        if (TubeTop.Verbose) { println(s)}
    }
    var result = 2
    fun changeResult() {
        result += 1;
        TubeBottom.Result = result
    }

    val top = TubeTop();
    val topkey = top.createTask(None());
    init {
        val map = MutableMapTaskDefs()
        setSize(1)
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
    var run = 0;
    override val changedFiles : Set<FSNode> = setOf( DiamondBottom.ResultTrigger);
    override fun setSize(size: Int) {
        DiamondTop.Shape = size
        run += 1;
        DiamondBottom.Result = size + run;
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
        //pieBuilder.withLogger(StreamLogger.verbose()  );
        pie = pieBuilder.build();
        setSize(1)
         println("BUILDING NEW PIE");
        pie.bottomUpObservableExecutor.requireTopDown(dt.createTask(None()));
    }
}

fun test_bench() {

    //TubeTop.Verbose = true;
    TubeEdge.AddSleep = true
    val steps = (1..10 step 1).toList()
    write_tube_csv ("${RESULT_DiR}tube_sleep/bh_tube_obs", listOf( obs_tube_trial( TubeGraph() , steps) ) , steps)
    println("DONE==============")
    write_tube_csv ("${RESULT_DiR}tube_sleep/bh_tube_no_obs", listOf( no_obs_tube_trial( TubeGraph() , steps) ) , steps)

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
    write_tube_csv("${RESULT_DiR}tube_comp/bh_obswarmup",warmups.map { obs_tube_trial( TubeGraph(),steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { obs_tube_trial( TubeGraph() ,steps) }.toList();
    write_tube_csv("${RESULT_DiR}tube_comp/bh_tube_obs",results,steps);

    write_tube_csv("${RESULT_DiR}tube_comp/bh_noobswarmup",warmups.map { no_obs_tube_trial( TubeGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { no_obs_tube_trial( TubeGraph(),steps )}.toList();
    write_tube_csv("${RESULT_DiR}tube_comp/bh_tube_no_obs",noresults,steps);
}
fun bench_tube_sleep() {
    TubeEdge.AddSleep = true;
    // return test_bench()
    val steps = (1..10 step 1).toList();
    val warmups = (1..1);
    val trials = 1..3;
    write_tube_csv("${RESULT_DiR}tube_sleep/bh_obswarmup",warmups.map { obs_tube_trial( TubeGraph(),steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { obs_tube_trial( TubeGraph() ,steps) }.toList();
    write_tube_csv("${RESULT_DiR}tube_sleep/bh_tube_obs",results,steps);
    write_tube_csv("${RESULT_DiR}tube_sleep/bh_noobswarmup",warmups.map { no_obs_tube_trial( TubeGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { no_obs_tube_trial( TubeGraph(),steps )}.toList();
    write_tube_csv("${RESULT_DiR}tube_sleep/bh_tube_no_obs",noresults,steps);
}





data class BenchResult(val noExec : Long, val execOnce : Long, val execTwice : Long )

fun obs_tube_trial(graph : TubeGraph, steps: List<Int>): List<BenchResult> {
    gc()

    //val p = StoreInspector()
    val changes = graph.changedResource();
    val trace = mutableListOf<Int>()
    graph.pie.bottomUpObservableExecutor.setObserver(graph.topkey.key()) {
        trace.add(it as Int)
    };
    val results =  steps.map{

        println("Obs(${it}):Refresh ")
        graph.setSize(it);
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);

        graph.log("Obs(${it}):TRIAL: ReObs")
        val startTime = System.nanoTime()
        graph.pie.bottomUpObservableExecutor.dropRootObserved(graph.topkey.key());
        //p.add_img(graph.pie.img())
        graph.pie.bottomUpObservableExecutor.addRootObserved(graph.topkey);
        val a = System.nanoTime() - startTime;
        //p.add_img(graph.pie.img())
        graph.log("Obs(${it}):TRIAL: ReObs Exec (Dropping)")
        val startTime2 = System.nanoTime()
        graph.pie.bottomUpObservableExecutor.dropRootObserved(graph.topkey.key());
        graph.changeResult()
        graph.log("Obs(${it}):Require (Unobs) ")
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);

        graph.log("Obs(${it}):Reobserve  ")
        graph.pie.bottomUpObservableExecutor.addRootObserved(graph.topkey);
        val b = System.nanoTime() - startTime2;

        graph.log("Obs(${it}):TRIAL:ReObs Exec Twice")
        val startTime3 = System.nanoTime()
        graph.pie.bottomUpObservableExecutor.dropRootObserved(graph.topkey.key());
        graph.log("Obs(${it}):Require (Unobs) ")
        graph.changeResult()
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);
        graph.log("Obs(${it}):Require (Unobs) ")
        graph.changeResult()
        graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);
        graph.log("Obs(${it}):Reobserve  ")
        graph.pie.bottomUpObservableExecutor.addRootObserved(graph.topkey);
        val c = System.nanoTime() - startTime3;
        BenchResult(a,b,c)
    }.toList();
    println("Trace OBS ${trace.size}")
    //SwingUtilities.invokeAndWait { p }
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
        println("Un(${it}):Refresh")
        graph.setSize(it);
        graph.changeResult()
        graph.pie.bottomUpExecutor.requireBottomUp(changes);

        graph.log("Un(${it}):TRIAL: Exec")
        val startTime2 = System.nanoTime()
        graph.log("Un(${it}):Require (obs) ")
        graph.changeResult();
        graph.pie.bottomUpExecutor.requireBottomUp(changes);
        val b = System.nanoTime() - startTime2;

        graph.log("Un(${it}):TRIAL: Exec Twice")
        val startTime3 = System.nanoTime()

        graph.log("Un(${it}):Require (obs) ")
        graph.changeResult();
        graph.pie.bottomUpExecutor.requireBottomUp(changes);
        graph.changeResult();
        graph.log("Un(${it}):Require (obs) ")
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
    val steps = forward + (forward.reversed()) + forward
    val warmups = (1..1)
    val trials = 1..3;
    write_csv("${RESULT_DiR}/diamond_sleep/bh_obswarmup",warmups.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList();
    write_csv("${RESULT_DiR}/diamond_sleep/bh_obstrial.csv",results,steps);


    write_csv("${RESULT_DiR}/diamond_sleep/bh_noobswarmup",warmups.map { diamond_no_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { diamond_no_obs_trial( WideDiamondGraph(),steps )}.toList();
    write_csv("${RESULT_DiR}/diamond_sleep/bh_noobstrial.csv",noresults,steps);

}
fun bench_diamond_comp() {
    DiamondEdge.AddSleep = false
    // return test_bench()
    val forward = (10..1000 step 10);
    val steps = forward + (forward.reversed()) + forward
    val warmups = (1..5)
    val trials = 1..10;


    write_csv(  "${RESULT_DiR}/diamond_comp/bh_noobswarmup",warmups.map { diamond_no_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps);
    val noresults = trials.map { diamond_no_obs_trial( WideDiamondGraph(),steps )}.toList();
    write_csv("${RESULT_DiR}/diamond_comp/bh_noobstrial.csv",noresults,steps);

    write_csv("${RESULT_DiR}/diamond_comp/bh_obswarmup",warmups.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList(),steps) ;
    /* write_csv("./obstrial.csv",results,steps); */
    val results = trials.map { diamond_obs_trial( WideDiamondGraph() ,steps) }.toList();
    write_csv("${RESULT_DiR}/diamond_comp/bh_obstrial.csv",results,steps);



}


fun diamond_obs_trial(graph : WideDiamondGraph, steps: List<Int>): List<Long> {
    gc()
    val trace = mutableListOf<Int>()
    val btt= graph.bt.createTask(None())
    graph.pie.bottomUpObservableExecutor.setObserver(btt.key() ) {
        trace.add(it as Int)
    };
    val changes = graph.changedResource();

    val result= steps.map{
                graph.setSize(it);
                val startTime = System.nanoTime()
                graph.pie.bottomUpObservableExecutor.requireBottomUp(changes);
        System.nanoTime() - startTime
            };
    println("${trace.size}")
    return result
}
fun diamond_no_obs_trial(graph : WideDiamondGraph,steps: List<Int>): List<Long> {
    gc()
    val trace = mutableListOf<Int>()
    val btt= graph.bt.createTask(None())
    graph.pie.bottomUpExecutor.setObserver(btt.key() ) {
        trace.add(it as Int)
    };
    val changes = graph.changedResource();

    val result = steps.map{
        graph.setSize(it);
        val startTime = System.nanoTime()
        graph.pie.bottomUpExecutor.requireBottomUp(changes);
        System.nanoTime() - startTime
    };
    println("${trace.size}")
    return result
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
