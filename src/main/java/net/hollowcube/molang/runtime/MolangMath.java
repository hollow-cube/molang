package net.hollowcube.molang.runtime;

import java.util.concurrent.ThreadLocalRandom;

public final class MolangMath {
    private static final int DIE_ROLL_ITERATION_LIMIT = Integer.getInteger(
        "molang.die-roll-iteration-limit",
        100
    );

    private static void assertion(boolean condition, String message) {
        if (!condition) {
            throw new MolangContentException(message);
        }
    }

    /// Absolute value of value
    public static double abs(double value) {
        return Math.abs(value);
    }

    /// arccos of value
    public static double acos(double value) {
        assertion(value >= -1 && value <= 1, "acos: value must be in the range [-1, 1]");
        return Math.toDegrees(Math.acos(value));
    }

    /// arcsin of value
    public static double asin(double value) {
        assertion(value >= -1 && value <= 1, "asin: value must be in the range [-1, 1]");
        return Math.toDegrees(Math.asin(value));
    }

    /**
     * arctan of value
     */
    public static double atan(double value) {
        return Math.toDegrees(Math.atan(value));
    }

    /// arctan of y/x. NOTE: the order of arguments!
    public static double atan2(double y, double x) {
        return Math.toDegrees(Math.atan2(y, x));
    }

    /// Round value up to nearest integral number
    public static double ceil(double value) {
        return Math.ceil(value);
    }

    /// Clamp value to between min and max inclusive
    public static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    /// Compose a floating-point value with the magnitude of a and the sign of b.
    public static double copySign(double a, double b) {
        return Math.copySign(a, b);
    }

    /// Cosine (in degrees) of value
    public static double cos(double value) {
        return Math.cos(Math.toRadians(value));
    }

    /// Returns the sum of 'num' random numbers, each with a value from low to high. Note: the generated random numbers are not integers like normal dice. For that, use math.die_roll_integer.
    public static double dieRoll(double num, double low, double high) {
        double total = 0;
        for (int i = 0; i < Math.clamp(num, 0, DIE_ROLL_ITERATION_LIMIT); i++)
            total += random(low, high);
        return total;
    }

    /// Returns the sum of 'num' random integer numbers, each with a value from low to high. Note: the generated random numbers are integers like normal dice.
    public static double dieRollInteger(double num, double low, double high) {
        double total = 0;
        for (int i = 0; i < Math.clamp(num, 0, DIE_ROLL_ITERATION_LIMIT); i++)
            total += randomInteger(low, high);
        return total;
    }

    ///  Output goes from start to end via zeroToOne, overshooting backward before accelerating into the end
    public static double easeInBack(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInBack.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting with bounce oscillations and settling into the end
    public static double easeInBounce(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInBounce.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow and accelerating along a circular curve toward the end
    public static double easeInCirc(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInCirc.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow and accelerating rapidly toward the end
    public static double easeInCubic(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInCubic.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting with elastic oscillations before accelerating into the end
    public static double easeInElastic(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInElastic.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow and accelerating extremely rapidly toward the end
    public static double easeInExpo(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInExpo.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, overshooting at both start and end, with smoother change in the middle
    public static double easeInOutBack(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutBack.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting and ending with bounce oscillations, smoother in the middle
    public static double easeInOutBounce(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutBounce.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting and ending slow, with circular acceleration and deceleration in the middle
    public static double easeInOutCirc(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutCirc.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow, accelerating rapidly in the middle, then slowing again at the end
    public static double easeInOutCubic(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutCubic.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, oscillating elastically at both start and end, with stable change in the middle
    public static double easeInOutElastic(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutElastic.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting and ending slow, with extremely rapid change in the middle
    public static double easeInOutExpo(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutExpo.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow, accelerating in the middle, then slowing again at the end
    public static double easeInOutQuad(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutQuad.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow, accelerating very rapidly in the middle, then slowing again at the end
    public static double easeInOutQuart(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutQuart.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow, accelerating extremely rapidly in the middle, then slowing again at the end
    public static double easeInOutQuint(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutQuint.apply(zeroToOne);

        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting and ending slow, with smoother change in the middle
    public static double easeInOutSine(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInOutSine.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow and accelerating toward the end
    public static double easeInQuad(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInQuad.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow and accelerating very rapidly toward the end
    public static double easeInQuart(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInQuart.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow and accelerating extremely rapidly toward the end
    public static double easeInQuint(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInQuint.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting slow and accelerating smoothly toward the end
    public static double easeInSine(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeInSine.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, overshooting past the end before settling into it
    public static double easeOutBack(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutBack.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, approaching the end with bounce oscillations that diminish over time
    public static double easeOutBounce(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutBounce.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting fast and decelerating along a circular curve toward the end
    public static double easeOutCirc(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutCirc.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting fast and decelerating rapidly toward the end
    public static double easeOutCubic(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutCubic.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, overshooting the end with elastic oscillations before settling
    public static double easeOutElastic(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutElastic.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting extremely fast and decelerating gradually toward the end
    public static double easeOutExpo(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutExpo.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting fast and decelerating toward the end
    public static double easeOutQuad(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutQuad.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting fast and decelerating very rapidly toward the end
    public static double easeOutQuart(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutQuart.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting fast and decelerating extremely rapidly toward the end
    public static double easeOutQuint(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutQuint.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Output goes from start to end via zeroToOne, starting fast and decelerating smoothly toward the end
    public static double easeOutSine(double start, double end, double zeroToOne) {
        double f = EasingFunction.easeOutSine.apply(zeroToOne);
        return start + (end - start) * f;
    }

    /// Calculates e to the value 'nth' power
    public static double exp(double value) {
        return Math.exp(value);
    }

    /// Round value down to nearest integral number
    public static double floor(double value) {
        return Math.floor(value);
    }

    /// Useful for simple smooth curve interpolation using one of the Hermite Basis functions: 3t^2 - 2t^3. Note that while any valid float is a valid input, this function works best in the range [0,1].
    public static double hermiteBlend(double value) {
        return 3 * value * value - 2 * value * value * value;
    }

    /// Returns the normalized progress between start and end given value
    public static double inverseLerp(double start, double end, double value) {
        assertion(start != end, "inverse_lerp: start and end cannot be equal");
        return (value - start) / (end - start);
    }

    /// Lerp from start to end via zeroToOne. zeroToOne is not clamped, so values outside [0, 1] extrapolate.
    public static double lerp(double start, double end, double zeroToOne) {
        return start + (end - start) * zeroToOne;
    }

    /// Lerp the shortest direction around a circle from start degrees to end degrees via zeroToOne
    ///
    /// Matches Blockbench (molangjs): both angles are wrapped into [0, 360) and the lerp runs from
    /// whichever of the two is smaller, so the result is in [0, 360) and zeroToOne is not clamped.
    public static double lerprotate(double start, double end, double zeroToOne) {
        double a = wrapDegrees(start), b = wrapDegrees(end);
        if (a > b) {
            double swap = a;
            a = b;
            b = swap;
        }
        double diff = b - a;
        if (diff > 180) return wrapDegrees(b + zeroToOne * (360 - diff));
        return a + zeroToOne * diff;
    }

    private static double wrapDegrees(double value) {
        return (((value + 180) % 360) + 180) % 360;
    }

    /// Natural logarithm of value
    public static double ln(double value) {
        assertion(value > 0, "ln: value must be positive");
        return Math.log(value);
    }

    /// Return highest value of A or B
    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    /// Return lowest value of A or B
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    /// Minimize angle magnitude (in degrees) into the range [-180, 180)
    public static double minAngle(double value) {
        value = value % 360;
        if (value >= 180) {
            return value - 360;
        } else if (value < -180) {
            return value + 360;
        } else {
            return value;
        }
    }

    /// Return the remainder of value / denominator
    public static double mod(double value, double denominator) {
        assertion(denominator != 0, "mod: denominator cannot be zero");
        return value % denominator;
    }

    /// Returns the float representation of the constant pi.
    public static double pi() {
        return Math.PI;
    }

    /// Elevates base to the exponent'th power
    public static double pow(double base, double exponent) {
        assertion(base != 0 || exponent >= 0, "pow: cannot raise 0 to a negative exponent");
        double result = Math.pow(base, exponent);
        assertion(!Double.isNaN(result), "pow: negative base requires an integer exponent");
        return result;
    }

    /// Random value between low and high inclusive. The bounds may be given in either order.
    public static double random(double low, double high) {
        if (low > high) {
            double swap = low;
            low = high;
            high = swap;
        }
        if (low == high) return low;
        return ThreadLocalRandom.current().nextDouble(low, Math.nextUp(high));
    }

    /// Random integer value between low and high inclusive. The bounds may be given in either order.
    public static double randomInteger(double low, double high) {
        long min = (long) Math.min(low, high), max = (long) Math.max(low, high);
        if (min == max) return min;
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /// Round value to nearest integral number
    public static double round(double value) {
        return Math.round(value);
    }

    /// Returns 1 if value is positive, -1 if it is negative, 0 if it is zero
    ///
    /// Note: sign(0) is 0 to match Blockbench (molangjs), where the Bedrock documentation says -1.
    public static double sign(double value) {
        return Math.signum(value);
    }

    /// Sine (in degrees) of value
    public static double sin(double value) {
        return Math.sin(Math.toRadians(value));
    }

    /// Square root of value
    public static double sqrt(double value) {
        assertion(value >= 0, "sqrt: value cannot be negative");
        return Math.sqrt(value);
    }

    /// Round value towards zero
    public static double trunc(double value) {
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }
}
