package com.taxiapp.ui.auth

import com.taxiapp.R

object CountryUtils {
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

