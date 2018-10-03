package mb.pie.api.stamp.path

import mb.pie.api.stamp.FileStamp
import mb.pie.api.stamp.FileStamper
import mb.pie.vfs.path.PPath
import java.sql.Time
import java.time.Duration
import java.time.Instant

class FreshnessStamper(dt: Duration) : FileStamper {
    var prevStamp : Instant = Instant.now()
    val dt = dt;
    override fun stamp(path: PPath): FileStamp {
        if( Duration.between(prevStamp,Instant.now()) > dt ) {
            prevStamp = Instant.now()

        }
        return ValueFileStamp(prevStamp,this)
    }

    override fun toString(): String {
        return "FreshnessStamper"
    }
}
