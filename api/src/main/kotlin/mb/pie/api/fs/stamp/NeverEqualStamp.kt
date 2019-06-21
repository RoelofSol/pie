package mb.pie.api.fs.stamp
import java.time.Duration
import java.time.Instant
import mb.pie.api.fs.FileSystemResource
import mb.pie.api.stamp.ResourceStamper

class NeverEqualStamp() : FileSystemStamper {

  class NotEqualStamp(override val stamper: ResourceStamper<FileSystemResource>) : FileSystemStamp {
    override fun equals(other: Any?): Boolean {
      return false
    }
  }

  override fun stamp(resource: FileSystemResource): FileSystemStamp {
    return NotEqualStamp(this)
  }

  override fun toString(): String {
    return "NotEqualStamper"
  }
}

