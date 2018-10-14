package mb.pie.runtime.store

import mb.pie.api.*
import mb.pie.vfs.path.PPath
import java.util.concurrent.ConcurrentHashMap

class InMemoryStore : Store, StoreReadTxn, StoreWriteTxn {
  private val inputs = ConcurrentHashMap<TaskKey, In>()
  private val outputs = ConcurrentHashMap<TaskKey, Output<*>>()
  private val taskReqs = ConcurrentHashMap<TaskKey, ArrayList<TaskReq>>()
  private val callersOf = ConcurrentHashMap<TaskKey, MutableSet<TaskKey>>()
  private val fileReqs = ConcurrentHashMap<TaskKey, ArrayList<FileReq>>()
  private val requireesOf = ConcurrentHashMap<PPath, MutableSet<TaskKey>>()
  private val fileGens = ConcurrentHashMap<TaskKey, ArrayList<FileGen>>()
  private val generatorOf = ConcurrentHashMap<PPath, TaskKey?>()
  private val observables = ConcurrentHashMap<TaskKey,Observability>()

  override fun readTxn() = this
  override fun writeTxn() = this
  override fun sync() {}
  override fun close() {}


  override fun input(key: TaskKey) = inputs[key]
  override fun setInput(key: TaskKey, input: In) {
    inputs[key] = input
  }

  override fun output(key: TaskKey) = if(!outputs.containsKey(key)) {
    null
  } else {
    val wrapper = outputs[key]!!
    Output(wrapper.output)
  }

  override fun setOutput(key: TaskKey, output: Out) {
    // ConcurrentHashMap does not support null values, so also wrap outputs (which can be null) in an Output object.
    outputs[key] = Output(output)
  }

  override fun taskReqs(key: TaskKey) = taskReqs.getOrEmptyList(key)
  override fun callersOf(key: TaskKey): Set<TaskKey> = callersOf.getOrPutSet(key)
  override fun observability(key: TaskKey) : Observability = observables.getOrDefault(key,Observability.Attached)
  override fun setTaskReqs(key: TaskKey, taskReqs: ArrayList<TaskReq>) {
    // Remove old call requirements
    val oldTaskReqs = this.taskReqs.remove(key)
    if(oldTaskReqs != null) {
      for(taskReq in oldTaskReqs) {
        callersOf.getOrPutSet(taskReq.callee).remove(key)
      }
    }
    // OPTO: diff taskReqs and oldCallReqs, remove/add entries based on diff.
    // Add new call requirements
    this.taskReqs[key] = taskReqs
    for(taskReq in taskReqs) {
      callersOf.getOrPutSet(taskReq.callee).add(key)
    }
  }

  override fun fileReqs(key: TaskKey) = fileReqs.getOrEmptyList(key)
  override fun requireesOf(file: PPath): Set<TaskKey> = requireesOf.getOrPutSet(file)
  override fun setFileReqs(key: TaskKey, fileReqs: ArrayList<FileReq>) {
    // Remove old file requirements
    val oldFileReqs = this.fileReqs.remove(key)
    if(oldFileReqs != null) {
      for(fileReq in oldFileReqs) {
        requireesOf.getOrPutSet(fileReq.file).remove(key)
      }
    }
    // OPTO: diff fileReqs and oldPathReqs, remove/add entries based on diff.
    // Add new call requirements
    this.fileReqs[key] = fileReqs
    for(fileReq in fileReqs) {
      requireesOf.getOrPutSet(fileReq.file).add(key)
    }
  }

  override fun fileGens(key: TaskKey) = fileGens.getOrEmptyList(key)
  override fun generatorOf(file: PPath): TaskKey? = generatorOf[file]
  override fun setFileGens(key: TaskKey, fileGens: ArrayList<FileGen>) {
    // Remove old file generators
    val oldFileGens = this.fileGens.remove(key)
    if(oldFileGens != null) {
      for(fileGen in oldFileGens) {
        generatorOf.remove(fileGen.file)
      }
    }
    // OPTO: diff fileGens and oldPathGens, remove/add entries based on diff.
    // Add new file generators
    this.fileGens[key] = fileGens
    for(fileGen in fileGens) {
      generatorOf[fileGen.file] = key
    }
  }

  override fun data(key: TaskKey): TaskData<*, *>? {
    val input = input(key) ?: return null
    val output = output(key) ?: return null
    val callReqs = taskReqs(key)
    val pathReqs = fileReqs(key)
    val pathGens = fileGens(key)
    val observable = observability(key)
    return TaskData(input, output.output, callReqs, pathReqs, pathGens,observable)
  }

  override fun setData(key: TaskKey, data: TaskData<*, *>) {
    val (input, output, callReqs, pathReqs, pathGens,observability) = data
    setInput(key, input)
    setOutput(key, output)
    setTaskReqs(key, callReqs)
    setFileReqs(key, pathReqs)
    setFileGens(key, pathGens)
    setObservability(key, observability)


  }


    override fun setObservability(key: TaskKey, observability: Observability) {
        observables[key] = observability;
    }


  override fun numSourceFiles(): Int {
    var numSourceFiles = 0
    for(file in requireesOf.keys) {
      if(!generatorOf.containsKey(file)) {
        ++numSourceFiles
      }
    }
    return numSourceFiles
  }


  override fun drop() {
    outputs.clear()
    taskReqs.clear()
    callersOf.clear()
    fileReqs.clear()
    requireesOf.clear()
    fileGens.clear()
    generatorOf.clear()
  }


  override fun toString(): String {
    return "InMemoryStore"
  }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun <K, V> ConcurrentHashMap<K, MutableSet<V>>.getOrPutSet(key: K) = this.getOrPut(key) { ConcurrentHashMap.newKeySet<V>() }!!

@Suppress("NOTHING_TO_INLINE")
private inline fun <K, V> ConcurrentHashMap<K, ArrayList<V>>.getOrEmptyList(key: K) = this.getOrElse(key) { arrayListOf() }
