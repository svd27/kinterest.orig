package ch.passenger.kinterest.style

import ch.passenger.kinterest.service.KIApplication
import ch.passenger.kinterest.service.ServiceDescriptor
import ch.passenger.kinterest.service.SimpleServiceDescriptor
import ch.passenger.kinterest.service.InterestService
import ch.passenger.kinterest.Universe
import ch.passenger.kinterest.entityName
import ch.passenger.kinterest.neo4j.Neo4jDbWrapper
import ch.passenger.kinterest.service.KIService

/**
 * Created by svd on 18/01/2014.
 */
public fun styleApplication(db:Neo4jDbWrapper) : KIApplication {
    return KIApplication("style", styleServices(db))
}

public fun styleServices(db:Neo4jDbWrapper) : List<ServiceDescriptor<out KIService>> {
   // boostrapDomain(db)
    return listOf(
            SimpleServiceDescriptor(javaClass<InterestService<CSSStylesheet, Long>>()) {
                InterestService(Universe.galaxy(javaClass<CSSStylesheet>().entityName())!!)
            },
            SimpleServiceDescriptor(javaClass<InterestService<CSSStyleRule, Long>>()) {
                InterestService(Universe.galaxy(javaClass<CSSStyleRule>().entityName())!!)
            },
            SimpleServiceDescriptor(javaClass<InterestService<CSSProperty, Long>>()) {
                InterestService(Universe.galaxy(javaClass<CSSProperty>().entityName())!!)
            }
    )
}