package com.pulso.tv.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

fun Context.prefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.tokenPrefs(): SharedPreferences = getSharedPreferences("prefs2", Context.MODE_PRIVATE)
