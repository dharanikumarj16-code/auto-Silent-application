package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject

data class EmergencyContact(
    val name: String,
    val phone: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("phone", phone)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): EmergencyContact {
            return EmergencyContact(
                name = obj.optString("name", ""),
                phone = obj.optString("phone", "")
            )
        }

        fun listToJson(list: List<EmergencyContact>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        fun listFromJson(jsonStr: String): MutableList<EmergencyContact> {
            val list = mutableListOf<EmergencyContact>()
            if (jsonStr.isBlank()) return list
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(fromJsonObject(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }
}
