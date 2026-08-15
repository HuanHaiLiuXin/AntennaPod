package de.danoeh.antennapod.storage.database

import java.util.ArrayList
import java.util.Arrays

object CollectionTestUtil {

    fun <T> concat(item: T, list: List<out T>): List<out T> {
        val res = ArrayList(list)
        res.add(0, item)
        return res
    }

    fun <T> concat(list: List<out T>, item: T): List<out T> {
        val res = ArrayList(list)
        res.add(item)
        return res
    }

    fun <T> concat(list1: List<out T>, list2: List<out T>): List<out T> {
        val res = ArrayList(list1)
        res.addAll(list2)
        return res
    }

    fun <T> list(vararg a: T): List<T> {
        return Arrays.asList(*a)
    }
}
