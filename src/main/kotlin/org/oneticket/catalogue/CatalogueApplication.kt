package org.oneticket.catalogue

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class CatalogueApplication

fun main(args: Array<String>) {
    runApplication<CatalogueApplication>(*args)
}
