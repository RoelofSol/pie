package mb.pie.runtime.logger.exec

import mb.pie.api.*
import mb.pie.api.exec.ExecReason
import java.util.concurrent.atomic.AtomicInteger

class LoggerExecutorLogger @JvmOverloads constructor(
  private val logger: Logger,
  private val descLimit: Int = 200
) : ExecutorLogger {
  private var indentation = AtomicInteger(0)
  private val indent get() = " ".repeat(indentation.get())

  override fun requireTopDownInitialStart(key: TaskKey, task: Task<*, *>) {}
  override fun requireTopDownInitialEnd(key: TaskKey, task: Task<*, *>, output: Out) {}

  override fun requireTopDownStart(key: TaskKey, task: Task<*, *>) {
    logger.trace("${indent}v ${task.desc(descLimit)}")
    indentation.incrementAndGet()
  }

  override fun requireTopDownEnd(key: TaskKey, task: Task<*, *>, output: Out) {
    indentation.decrementAndGet()
    logger.trace("$indent✔ ${task.desc(descLimit)} -> ${output.toString().toShortString(descLimit)}")
  }


  override fun requireBottomUpInitialStart(changedFiles: Set<ResourceKey>) {}
  override fun requireBottomUpInitialEnd() {}


  override fun checkVisitedStart(key: TaskKey) {}
  override fun checkVisitedEnd(key: TaskKey, output: Out) {}

  override fun checkStoredStart(key: TaskKey) {}
  override fun checkStoredEnd(key: TaskKey, output: Out) {}

  override fun checkFileGenStart(key: TaskKey, task: Task<*, *>, fileGen: ResourceProvide) {}
  override fun checkFileGenEnd(key: TaskKey, task: Task<*, *>, fileGen: ResourceProvide, reason: ExecReason?) {
    if(reason != null) {
      if(reason is InconsistentResourceProvide) {
        logger.trace("$indent␦ ${fileGen.key} (inconsistent: ${fileGen.stamp} vs ${reason.newStamp})")
      } else {
        logger.trace("$indent␦ ${fileGen.key} (inconsistent)")
      }
    } else {
      logger.trace("$indent␦ ${fileGen.key} (consistent: ${fileGen.stamp})")
    }
  }

  override fun checkFileReqStart(key: TaskKey, task: Task<*, *>, fileReq: ResourceRequire) {}
  override fun checkFileReqEnd(key: TaskKey, task: Task<*, *>, fileReq: ResourceRequire, reason: ExecReason?) {
    if(reason != null) {
      if(reason is InconsistentResourceProvide) {
        logger.trace("$indent␦ ${fileReq.key} (inconsistent: ${fileReq.stamp} vs ${reason.newStamp})")
      } else {
        logger.trace("$indent␦ ${fileReq.key} (inconsistent)")
      }
    } else {
      logger.trace("$indent␦ ${fileReq.key} (consistent: ${fileReq.stamp})")
    }
  }

  override fun checkTaskReqStart(key: TaskKey, task: Task<*, *>, taskReq: TaskReq) {}
  override fun checkTaskReqEnd(key: TaskKey, task: Task<*, *>, taskReq: TaskReq, reason: ExecReason?) {
    when(reason) {
      is InconsistentTaskReq ->
        logger.trace("$indent␦ ${taskReq.callee.toShortString(descLimit)} (inconsistent: ${taskReq.stamp} vs ${reason.newStamp})")
      null ->
        logger.trace("$indent␦ ${taskReq.callee.toShortString(descLimit)} (consistent: ${taskReq.stamp})")
    }
  }


  override fun executeStart(key: TaskKey, task: Task<*, *>, reason: ExecReason) {
    logger.info("$indent> ${task.desc(descLimit)} (reason: $reason)")
  }

  override fun executeEnd(key: TaskKey, task: Task<*, *>, reason: ExecReason, data: TaskData<*, *>) {
    logger.info("$indent< ${data.output.toString().toShortString(descLimit)}")
  }


  override fun invokeObserverStart(observer: Function<Unit>, key: TaskKey, output: Out) {
    logger.trace("$indent@ ${observer.toString().toShortString(descLimit)}(${output.toString().toShortString(descLimit)})")
  }

  override fun invokeObserverEnd(observer: Function<Unit>, key: TaskKey, output: Out) {}
}