package info.skyblond.oam

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02X".format(eachByte) }

// https://en.wikipedia.org/wiki/Binary_prefix
private val humanSizeUnits = listOf(
    "", "K", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q"
)

fun Long.toHumanSize(): String {
    if (this < 1024) return this.toString()

    var unitIndex = 0
    var size = this.toBigDecimal().setScale(2)
    while (unitIndex < humanSizeUnits.size - 1 && size > 1000.0.toBigDecimal()) {
        unitIndex++
        size /= 1000.0.toBigDecimal()
    }
    return "%.1f".format(size) + humanSizeUnits[unitIndex]
}
