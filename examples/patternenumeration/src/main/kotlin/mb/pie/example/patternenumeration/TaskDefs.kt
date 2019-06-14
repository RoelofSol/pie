package mb.pie.example.patternenumeration

import mb.fs.java.JavaFSNode
import mb.fs.java.JavaFSPath
import mb.pie.api.ExecContext
import mb.pie.api.Key
import mb.pie.api.None
import mb.pie.api.TaskDef



class DynamicTask : TaskDef<Int, Int> {
    companion object {
        val currentState : Array<BooleanArray> = Array(4) { x -> booleanArrayOf(false,false,false,false)}
    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(id: Int): Int {
        for ( v in 0..3 ) {
            if (currentState[id][v]) {
                require(DynamicTask(),v);
            }
        }
        return 0;
    }

}

/*
class TreeBuilder : TaskDef<Branch.Input, None> {
    data class Input (val workspace : JavaFSPath) : Serializable
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: Input): None {


    }
}
class Branch : TaskDef<Branch.Input, None> {
    data class Input (val workspace : JavaFSPath) : Serializable
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: Input): None {


    }
}
        */