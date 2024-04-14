package altermarkive.guardian

class Helper {
    companion object {
        fun clamp(value: Int, min: Int, max: Int): Int {
            return if (value < min) min else if (value > max) max else value
        }
        fun clamp(value: Double, min1: Double, max1: Double, min2: Double, max2: Double): Double {
            return min2 + (value - min1) * (max2 - min2) / (max1 - min1)
        }
    }
}