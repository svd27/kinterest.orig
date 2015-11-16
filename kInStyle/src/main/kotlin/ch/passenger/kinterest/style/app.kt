package ch.passenger.kinterest.style

import ch.passenger.kinterest.Universe
import ch.passenger.kinterest.entityName
import ch.passenger.kinterest.neo4j.Neo4jDbWrapper
import ch.passenger.kinterest.service.*

/**
 * Created by svd on 18/01/2014.
 */
public fun styleApplication(db:Neo4jDbWrapper) : KIApplication {
    return KIApplication("style", styleServices(db))
}

public fun styleServices(db:Neo4jDbWrapper) : List<ServiceDescriptor<out KIService>> {
    boostrapDomain(db)
    return listOf(
            SimpleServiceDescriptor(InterestService::class.java) {
                InterestService<CSSStylesheet,Long>(Universe.galaxy(CSSStylesheet::class.java.entityName())!!)
            },
            SimpleServiceDescriptor(InterestService::class.java) {
                InterestService<CSSStyleRule,Long>(Universe.galaxy(CSSStyleRule::class.java.entityName())!!)
            },
            SimpleServiceDescriptor(InterestService::class.java) {
                InterestService<CSSProperty,Long>(Universe.galaxy(CSSProperty::class.java.entityName())!!)
            }
    )
}