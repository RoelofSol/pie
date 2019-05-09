package mb.pie.api

import java.io.Serializable

/**
 * Type for task inputs. It must adhere to the following properties:
 *
 * * Implements [Serializable].
 * * Implements [equals][Object.equals] and [hashCode][Object.hashCode].
 * * Must NOT be `null`.
 * * If they input is used as a [key][Key], it must also adhere to [key][Key]'s properties.
 *
 * Failure to adhere to these properties will cause unsound incrementality.
 */
typealias In = Serializable

/**
 * Definition of an executable task.
 */
interface TaskDef<I : In, O : Out> {
  /**
   * Unique identifier of the task definition.
   */
  val id: String

  /**
   * Returns a key that uniquely identifies the task for given input.
   */
  @JvmDefault
  fun key(input: I): Key = input

  /**
   * Executes the task with given input, and returns its output.
   *
   * @throws ExecException when execution of the task fails unexpectedly.
   * @throws InterruptedException when execution of the task is cancelled or otherwise interrupted.
   */
  @Throws(ExecException::class, InterruptedException::class)
  fun ExecContext.exec(input: I): O

  /**
   * Returns the description of task for given [input].
   */
  @JvmDefault
  fun desc(input: I): String = "$id($input)"

  /**
   * Returns the description of task for given [input], with given [maximum length][maxLength].
   */
  @JvmDefault
  fun desc(input: I, maxLength: Int = 100): String = "$id(${input.toString().toShortString(maxLength)})"

  /**
   * Creates a task instance with given [input] for this task definition.
   */
  @JvmDefault
  fun createTask(input: I): Task<I, O> = Task(this, input)

  /**
   * Creates a serializable task instance with given [input] for this task definition.
   */
  @JvmDefault
  fun createSerializableTask(input: I): STask<I> = STask(this.id, input)

  /**
   * Ask if task instance can be garbage collected when its no longer referenced.
   */
  @JvmDefault
  fun removeUnused(input: I): Boolean = false
}

/**
 * [TaskDef] implementation using anonymous functions.
 */
open class LambdaTaskDef<I : In, O : Out>(
  override val id: String,
  private val execFunc: ExecContext.(I) -> O,
  private val keyFunc: ((I) -> Key)? = null,
  private val descFunc: ((I, Int) -> String)? = null
) : TaskDef<I, O> {
  override fun ExecContext.exec(input: I): O = execFunc(input)
  override fun key(input: I) = keyFunc?.invoke(input) ?: super.key(input)
  override fun desc(input: I, maxLength: Int): String = descFunc?.invoke(input, maxLength) ?: super.desc(input, maxLength)
  override fun removeUnused(input: I): Boolean = true
}

/**
 * Collection of [task definitions][TaskDef].
 */
interface TaskDefs {
  fun <I : In, O : Out> getTaskDef(id: String): TaskDef<I, O>?
}
