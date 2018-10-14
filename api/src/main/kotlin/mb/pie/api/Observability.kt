package mb.pie.api

import java.io.Serializable


enum class Observability : Serializable {
    Attached,
    ForcedDetached,
    Detached
}
