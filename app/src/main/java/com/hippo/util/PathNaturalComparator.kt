package com.hippo.util

/**
 * Compares each path segment in natural sort order.
 * `\` and `/` are the same. Duplicate `\` and `/` are ignored.
 * Leading `\` and `/` are ignored, so there is no difference between
 * relative path and absolute path.
 * `/./` and `/../` are treated as normal path.
 */
class PathNaturalComparator : Comparator<String?> {
    private val naturalComparator: Comparator<String?> = NaturalComparator()

    override fun compare(o1: String?, o2: String?): Int {
        if (o1 == null && o2 == null) {
            return 0
        }
        if (o1 == null) {
            return -1
        }
        if (o2 == null) {
            return 1
        }

        var index1 = 0
        var index2 = 0

        while (true) {
            val data1: String?
            val data2: String?

            while (true) {
                val newIndex1: Int = nextSegmentStart(o1, index1)
                if (newIndex1 == index1) {
                    data1 = null
                    break
                }
                if (getType(o1[newIndex1 - 1])) {
                    data1 = o1.substring(index1, newIndex1)
                    index1 = newIndex1
                    break
                }
                index1 = newIndex1
            }

            while (true) {
                val newIndex2: Int = nextSegmentStart(o2, index2)
                if (newIndex2 == index2) {
                    data2 = null
                    break
                }
                if (getType(o2[newIndex2 - 1])) {
                    data2 = o2.substring(index2, newIndex2)
                    index2 = newIndex2
                    break
                }
                index2 = newIndex2
            }

            if (data1 == null && data2 == null) {
                return 0
            }
            if (data1 == null) {
                return -1
            }
            if (data2 == null) {
                return 1
            }

            val result = naturalComparator.compare(data1, data2)
            if (result != 0) {
                return result
            }
        }
    }

    companion object {
        private const val TYPE_SEPARATOR = false
        private const val TYPE_NORMAL = true

        private fun nextSegmentStart(str: String, index: Int): Int {
            if (index >= str.length) {
                return index
            }

            val type: Boolean = getType(str[index])
            var i = index + 1
            while (i < str.length && type == getType(str[i])) {
                i += 1
            }

            return i
        }

        private fun getType(c: Char): Boolean {
            return if (c == '/' || c == '\\') {
                TYPE_SEPARATOR
            } else {
                TYPE_NORMAL
            }
        }
    }
}
