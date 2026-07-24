import java.util.Calendar

fun calc(h12: Int, m: Int, isNorth: Boolean): Double {
    val angleFrom12 = (h12 * 30.0 + m * 0.5)
    return if (isNorth) {
        (360.0 - angleFrom12 / 2.0) % 360.0
    } else {
        (180.0 - angleFrom12 / 2.0 + 360.0) % 360.0
    }
}
println("3:00 North: " + calc(3, 0, true))
println("9:00 North: " + calc(9, 0, true))
println("15:00 North (represented as 3:00 on 12h clock): " + calc(3, 0, true))
