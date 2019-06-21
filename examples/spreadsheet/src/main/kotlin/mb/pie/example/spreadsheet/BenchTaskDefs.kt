package mb.pie.example.spreadsheet

import mb.fs.java.JavaFSNode
import mb.fs.java.JavaFSPath
import mb.pie.api.ExecContext
import mb.pie.api.Key
import mb.pie.api.None
import mb.pie.api.TaskDef
import mb.pie.api.fs.stamp.FileSystemStampers
import java.io.Serializable
import java.lang.Thread.sleep


class TubeTop : TaskDef<None, Int> {
    companion object {
        var ShapeTrigger = JavaFSNode("/dev/null")
        var Shape = 1
        var Verbose = false

    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: None): Int {
        require(ShapeTrigger,FileSystemStampers.always_dirty);
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
        return  if (input == 0) {require(TubeBottom(),None()); } else { require(TubeEdge(),input -1)}
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
        require(ResultTrigger,FileSystemStampers.always_dirty);
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
        require(ShapeTrigger,FileSystemStampers.always_dirty);
        //val bytes = ShapeTrigger.readAllBytes()
        var sum = 0
     //   println("EXEC==== Diamond Top OF size ${Shape.size}");
        for ( line in 0..Shape) {
            sum += require(DiamondEdge(), line)
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
        require(ResultTrigger,FileSystemStampers.always_dirty);
        return Result
    }
}

