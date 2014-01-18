package ch.passenger.kinterest.util

import java.util.HashMap

/**
 * Created by svd on 16/12/13.
 */
fun AutoCloseable.with<T>(run: () -> T?) {
    try {
        run()
    } finally {
        close()
    }
}
fun<K,V> Map<K,V>.filter(predicate: (Pair<K,V>)->Boolean) : Map<K,V> {
    val res : MutableMap<K,V> = HashMap()
    this.entrySet().forEach { if(predicate(Pair(it.key,it.value))) res[it.key] = it.value}
    return res
}

fun<T> Array<T>.firstThat(predicate:(T)->Boolean) : T? {
    for(t in this) if(predicate(t)) return t
    return null
}

fun<T> Iterable<T>.firstThat(predicate:(T)->Boolean) : T? {
    for(t in this) if(predicate(t)) return t
    return null
}
