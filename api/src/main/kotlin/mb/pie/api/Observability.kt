package mb.pie.api

import java.io.Serializable


enum class Observability : Serializable {
    Observed,
    RootObserved,
    Detached;
    fun isObservable(): Boolean = this == RootObserved || this == Observed
    fun      isNotObservable() : Boolean = !this.isObservable()
}

fun addOutput(store: StoreWriteTxn, key : TaskKey){
    store.setObservability(key,Observability.RootObserved)
}


fun dropOutput(store: StoreWriteTxn, key : TaskKey){
    val isObserved = store.callersOf(key).map { k -> store.observability(k) }.any{ it.isObservable()}
    if (isObserved ) {
        store.setObservability(key,Observability.Observed)
    } else {
        store.setObservability(key,Observability.Detached)
        for (reqs in store.taskRequires(key)) {
            propegateDetachment(store,reqs.callee)
        }
    }
}

fun propegateDetachment(store: StoreWriteTxn,key: TaskKey) {
    if (store.observability(key).isNotObservable()) {
        return
    }
    val has_attached_parent = store.callersOf(key).map { k -> store.observability(k) }.any{ it.isObservable()}
    if (has_attached_parent) {
        return
    }
    store.setObservability(key, Observability.Detached)
    for (reqs in store.taskRequires(key)) {
        propegateDetachment(store, reqs.callee)
    }
}
