package ch.passenger.kinterest.util.slf4j

import org.slf4j.Logger

/**
 * Created by svd on 12/12/13.
 */
inline fun Logger.info(log:()->String) {
    if(this.isInfoEnabled()) info(log())
}

inline fun Logger.debug(log:()->String) {
    if(this.isDebugEnabled()) debug(log())
}