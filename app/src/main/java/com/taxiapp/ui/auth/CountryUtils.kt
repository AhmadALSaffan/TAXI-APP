package com.taxiapp.ui.auth

import com.taxiapp.R

data class Country(
    val flag: String,
    val name: String,
    val dialCode: String
)

object CountryUtils {
    /** Flag emoji for an ISO 3166 alpha-2 code ("US" → 🇺🇸), used to match the countries list. */
    fun flagFor(isoCode: String): String? {
        val code = isoCode.uppercase()
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return null
        return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    fun getCountries(context: android.content.Context): List<Country> {
        return context.resources
            .getStringArray(R.array.countries_array)
            .map { entry ->
                val parts      = entry.split("|")
                val flagAndName = parts[0].trim()
                val dialCode   = parts[1].trim()
                val spaceIndex = flagAndName.indexOf(" ")
                val flag       = flagAndName.substring(0, spaceIndex).trim()
                val name       = flagAndName.substring(spaceIndex).trim()
                Country(flag, name, dialCode)
            }
    }
}

