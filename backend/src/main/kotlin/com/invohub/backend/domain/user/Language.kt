package com.invohub.backend.domain.user

import java.util.Locale

/** The languages InvoHub is available in. Each user has one, used for the app and the emails they get. */
enum class Language(
    val locale: Locale,
) {
    EN(Locale.ENGLISH),
    EL(Locale.of("el")),
    ;

    companion object {
        val DEFAULT = EN
    }
}
