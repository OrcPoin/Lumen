package com.yourcompany.booru.ui

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

fun loadBooruOptions(sharedPreferences: SharedPreferences): MutableList<String> {
    val defaultBoorus = listOf(
        "https://rule34.xxx/",
        "https://bgb.booru.org/",
        "https://rule34.booru.org/"
    )
    val savedBoorusJson = sharedPreferences.getString("booruOptions", null)
    return if (savedBoorusJson != null) {
        val type = object : TypeToken<MutableList<String>>() {}.type
        Gson().fromJson(savedBoorusJson, type)
    } else {
        defaultBoorus.toMutableList()
    }
}