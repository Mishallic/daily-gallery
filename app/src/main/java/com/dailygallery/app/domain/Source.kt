package com.dailygallery.app.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The museums the artworks come from. Both publish their collections openly, with no key. */
@Serializable
enum class Source(val museum: String) {
    @SerialName("aic")
    AIC("Art Institute of Chicago"),

    @SerialName("cma")
    CMA("Cleveland Museum of Art"),
}
