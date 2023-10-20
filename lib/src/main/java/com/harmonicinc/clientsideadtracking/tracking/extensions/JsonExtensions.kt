package com.harmonicinc.clientsideadtracking.tracking.extensions

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

fun JSONObject.toMap(): Map<String, *> = keys().asSequence().associateWith {
    when (val value = this[it])
    {
        is JSONArray ->
        {
            val map = (0 until value.length()).associate { Pair(it.toString(), value[it]) }
            JSONObject(map).toMap().values.toList()
        }
        is JSONObject -> value.toMap()
        JSONObject.NULL -> null
        else            -> value
    }
}

fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until this.length()) {
        var value: Any = this[i]
        when (value) {
            is JSONArray -> value = value.toList()
            is JSONObject -> value = value.toMap()
        }
        list.add(value)
    }
    return list
}

fun JSONObject.optIntNull(name: String): Int? {
    val v = this.optInt(name)
    return if (v == 0) null else v
}

fun JSONObject.optDuration(name: String): Long {
    var v = this.optString(name)
    if (v.isEmpty()) return 0
    val arr: List<String> = v.split(":")
    var duration: Duration = Duration.ZERO
    if (arr.size == 2) {
        v = "PT" + arr[0] + "M" + arr[1] + "S"
        duration = Duration.parse(v)
    }
    return duration.toMillis()
}