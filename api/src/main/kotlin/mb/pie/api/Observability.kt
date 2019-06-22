package mb.pie.api

import java.io.Serializable


enum class Observability : Serializable {
    Observed,
    RootObserved,
    Detached;
    fun isObservable(): Boolean = this == RootObserved || this == Observed
    fun isNotObservable() : Boolean = !this.isObservable()
}




fun propegateDetachment(store: StoreWriteTxn,key: TaskKey) {
    val observability = store.observability(key);
    if ( observability == Observability.Detached ){
        throw Error("Invalid Observability state. Please re-execute with the top down method")
    }

    // Ignore detachment if this task is RootObserved
    if ( observability == Observability.RootObserved ){ return }

    val has_attached_parent = store.callersOf(key).map { k -> store.observability(k) }.any{ it.isObservable()}
    if (has_attached_parent) {  return }


    store.setObservability(key, Observability.Detached)
    for (reqs in store.taskRequires(key)) {
        propegateDetachment(store, reqs.callee)
    }
}
