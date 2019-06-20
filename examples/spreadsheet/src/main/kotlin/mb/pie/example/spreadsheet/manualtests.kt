package mb.pie.example.spreadsheet

import mb.fs.java.JavaFSNode
import mb.fs.java.JavaFSPath
import mb.pie.api.*
import mb.pie.api.fs.FileSystemResource
import mb.pie.api.fs.stamp.FileSystemStampers
import mb.pie.runtime.PieBuilderImpl
import mb.pie.runtime.PieImpl
import mb.pie.runtime.logger.StreamLogger
import mb.pie.runtime.store.InMemoryStore
import mb.pie.runtime.taskdefs.MutableMapTaskDefs
import java.awt.BorderLayout
import java.awt.Label
import javax.swing.*
import javax.swing.event.DocumentListener


class Manual : TaskDef<Int, Int> {
    companion object {
        val States = Array<MutableSet<Int>>(5) { (it+1..4).toMutableSet()};
        fun reqFile(i:Int) : JavaFSNode { return JavaFSNode("/dev/${"../dev/".repeat(i)}/null")}
    }
    override val id: String = javaClass.simpleName
    override fun ExecContext.exec(input: Int): Int {
        for ( c in Manual.States[input]){
            require(Manual(),c)
        }
        require(reqFile(input), FileSystemStampers.always_dirty);
        // println("EXEC==== TubeEdge Top ${input}");
        return input;
    }
}


fun play(){

    val map = MutableMapTaskDefs()
    val t = Manual();
    map.add(t.id, t);
    val pieBuilder = PieBuilderImpl()
    pieBuilder.withTaskDefs(map)
    pieBuilder.withLogger(StreamLogger.verbose()  );
    val pie = pieBuilder.build();
    pie.bottomUpObservableExecutor.requireTopDown(t.createTask(0));


    SwingUtilities.invokeAndWait {
        val inspector = StoreInspector()
        ManualUI(pie,inspector)
    }
}


class ManualUI(pie: PieImpl, inspector: StoreInspector) : JFrame() {
    private val pie = pie
    private val root_task = Manual().createTask(0)
    private val exec = pie.bottomUpObservableExecutor
    private val panel = JPanel()
    private val inspector = inspector

    init {
        title = "Manual"

        isVisible = true
        refresh()
        pack();
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    }



    class ManualTaskState(i: Int) : JTextField(20) {

        init {
            text = Manual.States[i].toString()
        }

    }

    fun refresh() {
        panel.removeAll()

        for ( i in 0 until Manual.States.size) {
            panel.add( ManualTaskState(i) , BorderLayout.CENTER)
        }

        inspector.add_img(pie.img())

        pack();

    }

}

