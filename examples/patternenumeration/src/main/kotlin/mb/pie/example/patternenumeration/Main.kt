package mb.pie.example.patternenumeration

import mb.fs.java.JavaFSNode
import mb.pie.api.StoreReadTxn
import mb.pie.api.TaskKey
import mb.pie.runtime.PieBuilderImpl
import mb.pie.runtime.logger.StreamLogger
import mb.pie.runtime.store.InMemoryStore
import mb.pie.runtime.taskdefs.MutableMapTaskDefs
import javax.swing.SwingUtilities


fun main(args: Array<String>) {

  val taskDefs = MutableMapTaskDefs()
  taskDefs.add(DynamicTask().id, DynamicTask())
  val pieBuilder = PieBuilderImpl()
  pieBuilder.withTaskDefs(taskDefs)
  pieBuilder.withLogger(StreamLogger.verbose()  )
  val pie = pieBuilder.build()


  val tasks = Array(4) {DynamicTask().createTask(it)}
  DynamicTask.currentState[0] = booleanArrayOf( false,true,true,true);




  pie.bottomUpObservableExecutor.requireTopDown(tasks[0])

  SwingUtilities.invokeAndWait {

    val inspector = StoreInspector()
    val store = pie.store.readTxn() as InMemoryStore;
    inspector.add_state(store.dump())

  }

  pie.close()
}

