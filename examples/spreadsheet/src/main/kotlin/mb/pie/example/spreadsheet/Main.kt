package mb.pie.example.spreadsheet

import mb.pie.api.*
import mb.pie.api.stamp.FileStampers
import mb.pie.runtime.PieBuilderImpl
import mb.pie.runtime.logger.StreamLogger
import mb.pie.runtime.taskdefs.MutableMapTaskDefs
import mb.pie.store.lmdb.LMDBStore
import mb.pie.store.lmdb.withLMDBStore
import mb.pie.vfs.path.PPath
import mb.pie.vfs.path.PathSrvImpl
import java.io.File
import java.io.Serializable
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.WatchService
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/**
 * The [FileCopier] task definition copies a file, generated by [FileGenerator] to another file. In this case, we need to take multiple
 * inputs, so we group them into the [FileCopier.Input] data class.
 */
class Cell : TaskDef<PPath, Int> {
  override val id: String = javaClass.simpleName
  override fun ExecContext.exec(input: PPath): Int {
    val bytes = input.readAllBytes()
    val lines = String(bytes).lines().filter({s -> s.isNotEmpty()})
    require(input,FileStampers.never_equal)
    var sum = 0
    for ( line in lines) {
       try {
          sum += line.toInt()
       } catch (e :  NumberFormatException) {
         val path : PPath = input.parent()?.resolve(line)!!
         val sub_sum = require(Cell(),path)
         sum += sub_sum
       }
    }
    return sum

  }
}

class Sheet : TaskDef<PPath, Int> {
  override val id: String = javaClass.simpleName
  override fun ExecContext.exec(input: PPath): Int {
      val path : PPath =  input.resolve("root")
      return require(Cell(),path)
  }
}

class MultiSheet : TaskDef<MultiSheet.Input, None> {
  data class Input ( val workspace : PPath , val inactive : Set<PPath> ) : Serializable
  override val id: String = javaClass.simpleName
  override fun ExecContext.exec(input: Input):None {

    for( entry in input.workspace.list() ) {
        if (entry.isDir() && !input.inactive.contains(entry)) {
           require(Sheet(),entry)
        }
    }
    return None()

  }
}


fun main(args: Array<String>) {
  // To work with paths that PIE can understand (PPath type), we create a PathSrv, and do some error checking.
  val pathSrv = PathSrvImpl()

  val workspace = pathSrv.resolveLocal("./workspace")

  // Now we instantiate the task definitions.
  val cell = Cell()
  val sheet = Sheet()
  val multi_sheet = MultiSheet()

  // Then, we add them to a TaskDefs object, which tells PIE about which task definitions are available.
  val taskDefs = MutableMapTaskDefs()
  taskDefs.add(cell.id, cell)
  taskDefs.add(sheet.id,sheet)
  taskDefs.add(multi_sheet.id,multi_sheet)



  // We need to create the PIE runtime, using a PieBuilderImpl.
  val pieBuilder = PieBuilderImpl()
  // We pass in the TaskDefs object we created.
  pieBuilder.withTaskDefs(taskDefs)
  // For storing build results and the dependency graph, we will use the LMDB embedded database, stored at target/lmdb.
  //pieBuilder.withLMDBStore(File("target/lmdb"))
  // For example purposes, we use verbose logging which will output to stdout.
  pieBuilder.withLogger(StreamLogger.verbose()  )
  // Then we build the PIE runtime.
  val pie = pieBuilder.build()

  // Now we create concrete task instances from the task definitions.


  val workspace_task = multi_sheet.createTask(MultiSheet.Input(workspace, emptySet()) )

  //val fileCopierTask = fileCopier.createTask(FileCopier.Input(sourceFile, fileCreatorTask.toSTask(), destinationFile))

  // We (incrementally) execute the file copier task using the top-down executor.
  pie.topDownExecutor.newSession().requireInitial(workspace_task)

  SwingUtilities.invokeAndWait {
    SpreadSheet(pie,workspace_task.key())
  }

  pie.close()
  pathSrv.close()
}



fun all_dependents(tx : StoreReadTxn , key : TaskKey) : Set<TaskKey> {
  var unchecked = tx.taskReqs(key).map { k -> k.callee }.toMutableList()
  var result = mutableSetOf<TaskKey>()

  while ( unchecked.size > 0 ) {
    val key = unchecked.removeAt(unchecked.size-1)
    result.add(key)
    unchecked.addAll( tx.taskReqs(key).map { k -> k.callee })
  }
  return result
}