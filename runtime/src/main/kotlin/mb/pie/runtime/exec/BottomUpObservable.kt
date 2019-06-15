package mb.pie.runtime.exec

import mb.pie.api.*
import mb.pie.api.exec.*
import mb.pie.api.fs.stamp.FileSystemStamper
import mb.pie.api.stamp.OutputStamper
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class BottomUpObservableExecutorImpl constructor(
        private val taskDefs: TaskDefs,
        private val resourceSystems: ResourceSystems,
        private val store: Store,
        private val share: Share,
        private val defaultOutputStamper: OutputStamper,
        private val defaultRequireFileSystemStamper: FileSystemStamper,
        private val defaultProvideFileSystemStamper: FileSystemStamper,
        private val layerFactory: (Logger) -> Layer,
        private val logger: Logger,
        private val executorLoggerFactory: (Logger) -> ExecutorLogger,
        override var dropPolicy : (Task<*,*>) -> Boolean  = Task<*,*>::removeUnused
) : BottomUpObservableExecutor {
  private val observers = ConcurrentHashMap<TaskKey, TaskObserver>()

  override fun dropRootObserved(key: TaskKey) {
    dropOutput(store.writeTxn(),key)
  }

   override fun addRootObserved(key: TaskKey) {

    requireTopDown(key.toTask(taskDefs,store.readTxn()))
    addOutput(store.writeTxn(),key)
  }

  override fun gc(): Int{
    var removed = 0;
    store.writeTxn().use {
      val txn = it as StoreReadTxn;
      val stack: Deque<TaskKey> = ArrayDeque()
      stack.addAll(txn.unobserved());
      while (stack.isNotEmpty()) {
        val key = stack.pop();
        val shouldDrop = try {
          dropPolicy(key.toTask(taskDefs, txn));
        } catch (e: Throwable) {
          true
        };
        if (shouldDrop) {
          removed += 1;
          val deps = it.dropKey(key)
          val unreferenced = deps.filter { txn.callersOf(it).isEmpty() };
          stack.addAll(unreferenced);
        }
      }
    };
    return removed
  }


  @Throws(ExecException::class)
  override fun <I : In, O : Out> requireTopDown(task: Task<I, O>): O {
    return requireTopDown(task, NullCancelled())
  }

  @Throws(ExecException::class, InterruptedException::class)
  override fun <I : In, O : Out> requireTopDown(task: Task<I, O>, cancel: Cancelled): O {
    val session = newSession()
    return session.requireTopDownInitial(task, cancel)
  }

  @Throws(ExecException::class)
  override fun requireBottomUp(changedResources: Set<ResourceKey>) {
    return requireBottomUp(changedResources, NullCancelled())
  }

  @Throws(ExecException::class, InterruptedException::class)
  override fun requireBottomUp(changedResources: Set<ResourceKey>, cancel: Cancelled) {
    if(changedResources.isEmpty()) return
    val session = newSession()
    session.requireBottomUpInitial(changedResources, cancel)
  }

  override fun hasBeenRequired(key: TaskKey): Boolean {
    return store.readTxn().use { it.output(key) } != null
  }

  override fun setObserver(key: TaskKey, observer: (Out) -> Unit) {
    observers[key] = observer
  }

  override fun removeObserver(key: TaskKey) {
    observers.remove(key)
  }

  override fun dropObservers() {
    observers.clear()
  }


  @Suppress("MemberVisibilityCanBePrivate")
  fun newSession(): BottomUpObservableSession {
    return BottomUpObservableSession(taskDefs, resourceSystems, observers, store, share, defaultOutputStamper, defaultRequireFileSystemStamper, defaultProvideFileSystemStamper, layerFactory(logger), logger, executorLoggerFactory(logger))
  }
}

open class BottomUpObservableSession(
  private val taskDefs: TaskDefs,
  private val resourceSystems: ResourceSystems,
  private val observers: Map<TaskKey, TaskObserver>,
  private val store: Store,
  share: Share,
  defaultOutputStamper: OutputStamper,
  defaultRequireFileSystemStamper: FileSystemStamper,
  defaultProvideFileSystemStamper: FileSystemStamper,
  private val layer: Layer,
  private val logger: Logger,
  private val executorLogger: ExecutorLogger
) : RequireTask {
  private val visited = mutableMapOf<TaskKey, TaskData<*, *>>()
  private val queue = DistinctTaskKeyPriorityQueue.withTransitiveDependencyComparator(store)
  private val executor = TaskExecutor(taskDefs, resourceSystems, visited, store, share, defaultOutputStamper, defaultRequireFileSystemStamper, defaultProvideFileSystemStamper, layer, logger, executorLogger) { key, data ->
    // Notify observer, if any.
    val observer = observers[key]
    if(observer != null) {
      val output = data.output
      executorLogger.invokeObserverStart(observer, key, output)
      observer.invoke(output)
      executorLogger.invokeObserverEnd(observer, key, output)
    }
  }
  private val requireShared = RequireShared(taskDefs, resourceSystems, visited, store, executorLogger)


  /**
   * Entry point for top-down builds.
   */
  open fun <I : In, O : Out> requireTopDownInitial(task: Task<I, O>, cancel: Cancelled = NullCancelled()): O {
    try {
      val key = task.key()
      executorLogger.requireTopDownInitialStart(key, task)
      val output = require(key, task, cancel)
      executorLogger.requireTopDownInitialEnd(key, task, output);
        addOutput(store.writeTxn(),key);
      return output
    } finally {
      store.sync()
    }
  }
    
  /**
   * Entry point for bottom-up builds.
   */
  open fun requireBottomUpInitial(changedResources: Set<ResourceKey>, cancel: Cancelled = NullCancelled()) {
    try {
      executorLogger.requireBottomUpInitialStart(changedResources)
      scheduleAffectedByResources(changedResources)
      execScheduled(cancel)
      executorLogger.requireBottomUpInitialEnd()
    } finally {
      store.sync()
    }
  }


  /**
   * Executes scheduled tasks (and schedules affected tasks) until queue is empty.
   */
  private fun execScheduled(cancel: Cancelled) {
    logger.trace("Executing scheduled tasks: $queue")
    while(queue.isNotEmpty()) {
      cancel.throwIfCancelled()

      val key = queue.poll()
      val task = store.readTxn().use { txn -> key.toTask(taskDefs, txn) }
      logger.trace("Polling: ${task.desc(200)}")
      execAndSchedule(key, task, cancel)
    }
  }

  /**
   * Executes given task, and schedules new tasks based on given task's output.
   */
  private fun <I : In, O : Out> execAndSchedule(key: TaskKey, task: Task<I, O>, cancel: Cancelled): TaskData<I, O> {
    val data = exec(key, task, AffectedExecReason(), cancel)
    scheduleAffectedCallersOf(key, data.output)
    return data
  }

  /**
   * Schedules tasks affected by (changes to) files.
   */
  private fun scheduleAffectedByResources(resources: Set<ResourceKey>) {
    logger.trace("Scheduling tasks affected by resources: $resources")
    val affected = store.readTxn().use { txn -> txn.directlyAffectedTaskKeys(resources, resourceSystems, logger) }
    for(key in affected) {
      logger.trace("- scheduling: $key")
      queue.add(key)
    }
  }

  /**
   * Schedules tasks affected by (changes to the) output of a task.
   */
  private fun scheduleAffectedCallersOf(callee: TaskKey, output: Out) {
    logger.trace("Scheduling tasks affected by output of: ${callee.toShortString(200)}")
    val inconsistentCallers = store.readTxn().use { txn ->
      txn.callersOf(callee).filter { caller ->
        txn.observability(caller).isObservable() &&
        txn.taskRequires(caller).filter { it.calleeEqual(callee) }.any { !it.isConsistent(output) }
      }
    }
    for(key in inconsistentCallers) {
      logger.trace("- scheduling: $key")
      queue.add(key)
    }
  }


  /**
   * Require the result of a task.
   */
  override fun <I : In, O : Out> require(key: TaskKey, task: Task<I, O>, cancel: Cancelled): O {
    Stats.addRequires()
    cancel.throwIfCancelled()
    layer.requireTopDownStart(key, task.input)
    executorLogger.requireTopDownStart(key, task)
    try {
      val data = getData(key, task, cancel)
      val output = data.output
      executorLogger.requireTopDownEnd(key, task, output)
      return output
    } finally {
      layer.requireTopDownEnd(key)
    }
  }

  data class DataW<I : In, O : Out>(val data: TaskData<I, O>, val executed: Boolean) {
    constructor(data: TaskData<I, O>) : this(data, true)
  }


  /**
   * Get data for given task/key, either by getting existing data or through execution.
   */
  private fun <I : In, O : Out> getData(key: TaskKey, task: Task<I, O>, cancel: Cancelled): TaskData<I, O>{
    // Check if task was already visited this execution. Return immediately if so.
    val visitedData = requireShared.dataFromVisited(key)
    if(visitedData != null) {
      return visitedData.cast<I, O>()
    }

    // Check if data is stored for task. Execute if not.
    val storedData = requireShared.dataFromStore(key)
    if(storedData == null) {
      // This tasks's output cannot affect other tasks since it is new. Therefore, we do not have to schedule new tasks.
      return  exec(key, task, NoData(), cancel)
    }

    val existingData = storedData.cast<I, O>();
    val (input, output, _, _, _, observable) = existingData;
    // We can not guarantee unobserved tasks are consistent
    if (observable.isNotObservable()) {
      return requireTopDownIncremental(key,task,existingData,cancel).cast<I,O>();
    }
    // Task is in dependency graph. It may be scheduled to be run, but we need its output *now*.
    val requireNowData = requireScheduledNow(key, cancel)
    if(requireNowData != null) {
      // Task was scheduled. That is, it was either directly or indirectly affected. Therefore, it has been executed.
      return requireNowData.cast<I, O>()
    }
    // Task was not scheduled. That is, it was not directly affected by file changes, and not indirectly affected by other tasks.
    // Therefore, it has not been executed. However, the task may still be affected by internal inconsistencies.

    // Internal consistency: input changes.
    with(requireShared.checkInput(input, task)) {
      if(this != null) {
        return  exec(key, task, this, cancel)
      }
    }

    // Internal consistency: transient output consistency.
    with(requireShared.checkOutputConsistency(output)) {
      if(this != null) {
        return  exec(key, task, this, cancel)
      }
    }

    // Notify observer.
    val observer = observers[key]
    if(observer != null) {
      executorLogger.invokeObserverStart(observer, key, output)
      observer.invoke(output)
      executorLogger.invokeObserverEnd(observer, key, output)
    }

    // Task is consistent.
    return  existingData

  }

  // This function is called when an Detached task is required again.
  // It recursivly calls itself to make its transitive closure consistent.
  private fun requireTopDownIncremental(key: TaskKey, task: Task<*,*>,oldData: TaskData<*,*>, cancel: Cancelled): TaskData<*,*> {

    // If a task in the transitive closure is Observed.
    if (oldData.observability.isObservable()) {
      // Check if task was already visited this execution. Return immediately if so.
      val visitedData = requireShared.dataFromVisited(key)
      if(visitedData != null) {
        return visitedData
      }

      // Task might be scheduled, so require now.
      val requireObs = requireScheduledNow(key,cancel)
      if(requireObs == null) {
        return exec(key, task, NoData(), cancel);
      }
      // It was Observed and thus consistent before this build.
      // It is not affected by a change, we can reuse the old result.
      visited[key] = requireObs;
      return requireObs
    }

     // If any files have changed we must execute;
     for (req in oldData.resourceRequires) {
         val inconsistent = requireShared.checkResourceRequire(key,task,req);
         if (inconsistent != null) { return exec(key,task,inconsistent,cancel)};
     }

    // All dependencies are made consistent with requireTopDownIncremental.
    // When task is unobserved, we requireTopDownIncremental all its children and compare their stamps
    for (taskRequire in oldData.taskRequires) {

      val (callee,stamp) = taskRequire;

      val calleeTask = store.readTxn().use { txn -> callee.toTask(taskDefs, txn) };

      val storedData = requireShared.dataFromStore(callee);
      if( storedData == null) { return exec(key,task,NoData(), cancel) }
      executorLogger.checkTaskRequireStart(key, task, taskRequire)
      val result : TaskData<*,*> = requireTopDownIncremental(callee, calleeTask,storedData,cancel );

      val newStamp = stamp.stamper.stamp(result.output);
      if (newStamp != stamp) {
        val reason = InconsistentTaskReq(taskRequire,newStamp)
        executorLogger.checkTaskRequireEnd(key, task, taskRequire, reason)
        return exec(key,task,NoData(),cancel)
      }
    }


    store.writeTxn().use{ txn -> txn.setObservability(key,Observability.Observed)}
    val result = store.readTxn().use{ txn -> txn.data(key)}!!;

    // All the dependencies of this task are in order. This task is consistent.
    // TODO: Verify that executing a 'sibling' dependency can never change the consistency of this task
    visited[key] = result
    return result
  }
  /**
   * Execute the scheduled dependency of a task, and the task itself, which is required to be run *now*.
   */
  private fun requireScheduledNow(key: TaskKey, cancel: Cancelled): TaskData<*, *>? {
    logger.trace("Executing scheduled (and its dependencies) task NOW: $key")
    while(queue.isNotEmpty()) {
      cancel.throwIfCancelled()
      val minTaskKey = queue.pollLeastTaskWithDepTo(key, store) ?: break
      val minTask = store.readTxn().use { txn -> minTaskKey.toTask(taskDefs, txn) }
      logger.trace("- least element less than task: ${minTask.desc()}")
      val data = execAndSchedule(minTaskKey, minTask, cancel)
      if(minTaskKey == key) {
        return data // Task was affected, and has been executed: return result
      }
    }
    return null // Task was not affected: return null
  }


  open fun <I : In, O : Out> exec(key: TaskKey, task: Task<I, O>, reason: ExecReason, cancel: Cancelled): TaskData<I, O> {
    return  executor.exec(key, task, reason, this, cancel);
  }





}
