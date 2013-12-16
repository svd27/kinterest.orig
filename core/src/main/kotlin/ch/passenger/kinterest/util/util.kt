package ch.passenger.kinterest.util

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
