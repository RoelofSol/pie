package mb.pie.runtime.exec

import mb.pie.api.*
import mb.pie.api.Observable
import mb.pie.api.exec.ExecReason
import mb.pie.vfs.path.PPath
import java.util.*

/**
 * Returns keys of tasks that are directly affected by changed files
 */
fun StoreReadTxn.directlyAffectedTaskKeys(changedFiles: Collection<PPath>, logger: Logger): HashSet<TaskKey> {
  val affected = HashSet<TaskKey>()
  val isAffected = { key : TaskKey ->
    if( observability(key) == Observable.Detached ) { logger.trace( "  * Is not attached ") }
    else { affected.add(key) }
  }


  for(changedFile in changedFiles) {
    logger.trace("* file: $changedFile")

    val requirees = requireesOf(changedFile)
    for(key in requirees) {
      logger.trace("  * required by: ${key.toShortString(200)}")
      if( observability(key) == Observable.Detached ) { logger.trace( "  * Is Detached ") }
      else if(!fileReqs(key).filter { it.file == changedFile }.all { it.isConsistent() }) {
        isAffected(key)
      }
    }

    val generator = generatorOf(changedFile)
    if(generator != null) {
      logger.trace("  * generated by: ${generator.toShortString(200)}")
      if( observability(generator) == Observable.Detached ) { logger.trace( "  * Is Detached ") }
      else if(!fileGens(generator).filter { it.file == changedFile }.all { it.isConsistent() }) {
        isAffected(generator)
      }
    }
  }

  return affected
}

/**
 * Checks whether [caller] has a transitive (or direct) task requirement to [callee].
 */
fun StoreReadTxn.hasTransitiveTaskReq(caller: TaskKey, callee: TaskKey): Boolean {
  // TODO: more efficient implementation for figuring out if an app transitively calls on another app?
  val toCheckQueue: Queue<TaskKey> = LinkedList()
  toCheckQueue.add(caller)
  while(!toCheckQueue.isEmpty()) {
    val toCheck = toCheckQueue.poll()
    val taskReqs = taskReqs(toCheck);
    if(taskReqs.any { it.calleeEqual(callee) }) {
      return true
    }
    toCheckQueue.addAll(taskReqs.map { it.callee })
  }
  return false
}

/**
 * [Execution reason][ExecReason] for when a task is (directly or indirectly) affected by a change.
 */
class AffectedExecReason : ExecReason {
  override fun toString() = "directly or indirectly affected by change"

  override fun equals(other: Any?): Boolean {
    if(this === other) return true
    if(other?.javaClass != javaClass) return false
    return true
  }

  override fun hashCode(): Int {
    return 0
  }
}
