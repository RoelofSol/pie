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

    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: None): Int {
        require(ShapeTrigger,FileSystemStampers.always_dirty);
       // println("EXEC==== TubeEdge Top ${input}");
        return  require(TubeEdge(),Shape);
    }
}

class TubeEdge : TaskDef<Int, Int> {
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: Int): Int {
       // println("EXEC==== TubeEdge Edge ${input}");

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
       // println("EXEC==== Tube Bottom ${input}");
        require(ResultTrigger,FileSystemStampers.always_dirty);
        return Result
    }
}





class DiamondTop : TaskDef<None, Double> {
    companion object {
        var ShapeTrigger = JavaFSNode("/dev/null")
        var Shape = listOf<Int>(1);
    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: None): Double {
        require(ShapeTrigger,FileSystemStampers.always_dirty);
        val bytes = ShapeTrigger.readAllBytes()
        var sum = 0.0
     //   println("EXEC==== Diamond Top OF size ${Shape.size}");
        for ( line in Shape) {
            sum += require(DiamondEdge(), line.toInt())
        }
        return sum
    }
}

class DiamondEdge : TaskDef<Int, Double> {
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: Int): Double {
      //  println("EXEC==== Diamond Edge ${input}");
        var result = require(DiamondBottom(),None()).toDouble();
       // sleep(10)
        return (result * input.toDouble())*2.0;
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

