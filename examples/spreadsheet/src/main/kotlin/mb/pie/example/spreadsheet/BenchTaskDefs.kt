package mb.pie.example.spreadsheet

import mb.fs.java.JavaFSNode
import mb.fs.java.JavaFSPath
import mb.pie.api.ExecContext
import mb.pie.api.Key
import mb.pie.api.None
import mb.pie.api.TaskDef
import mb.pie.api.fs.stamp.FileSystemStampers
import org.openjdk.jmh.infra.Blackhole
import java.io.Serializable
import java.lang.Thread.sleep


class TubeTop : TaskDef<None, Int> {
    companion object {
        var ShapeTrigger = JavaFSNode("/dev/null")
        var Shape = 1
        var Verbose = false

        val Hole =  Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")

    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: None): Int {

        if (TubeTop.Verbose) {println("EXEC====  Top ${input}");}
        return  require(TubeEdge(),Shape);
    }
}

class TubeEdge : TaskDef<Int, Int> {
    companion object {
        var AddSleep = false

    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: Int): Int {
        if (TubeTop.Verbose) {println("EXEC====  Edge ${input}");}
        if (AddSleep) { sleep(10) }
        val result =  if (input == 0) {require(TubeBottom(),None()); } else { require(TubeEdge(),input -1)}
        TubeTop.Hole.consume(result)
        return result
    }
}

class TubeBottom : TaskDef<None, Int> {
    companion object {
        var ResultTrigger = JavaFSNode("/dev/../dev/null")
        var Result = 1  ;
    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: None): Int {

        if (TubeTop.Verbose) {println("EXEC====  Bottom ${input}");}
        val stamp = require(ResultTrigger,FileSystemStampers.always_dirty);
        TubeTop.Hole.consume(stamp)
        return Result
    }
}





class DiamondTop : TaskDef<None, Int> {
    companion object {
        var ShapeTrigger = JavaFSNode("/dev/null")
        var Shape = 1
    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: None): Int {
        //val bytes = ShapeTrigger.readAllBytes()
        var sum = 0
     //   println("EXEC==== Diamond Top OF size ${Shape.size}");
        for ( line in 0..Shape) {
            sum += require(DiamondEdge(), line)
            TubeTop.Hole.consume(sum)
        }
        return sum
    }
}

class DiamondEdge : TaskDef<Int, Int> {
    companion object {
        var AddSleep = false
    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: Int): Int {
      //  println("EXEC==== Diamond Edge ${input}");
        var result = require(DiamondBottom(),None());
        if (DiamondEdge.AddSleep) { sleep(10) }
        TubeTop.Hole.consume(result)
        return (result * input);
    }
}


class DiamondBottom : TaskDef<None, Int> {
    companion object {
        var ResultTrigger = JavaFSNode("/dev/../dev/null")
        var Result = 1  ;
    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: None): Int {
  //      println("EXEC==== Diamond Bottom ${input}");
        val result = require(ResultTrigger,FileSystemStampers.always_dirty);
        TubeTop.Hole.consume(result)
        return Result
    }
}

