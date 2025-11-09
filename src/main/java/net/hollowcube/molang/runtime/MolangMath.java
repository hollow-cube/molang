package net.hollowcube.molang.runtime;

import net.hollowcube.molang.eval.MolangValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class MolangMath {
    private static final int DIE_ROLL_ITERATION_LIMIT = Integer.getInteger("molang.die-roll-iteration-limit", 100);

    public static final MolangValue.Holder MODULE = new HolderImpl();
    public static final Set<String> IMPURE_METHODS = Set.of(
            "die_roll", "die_roll_integer",
            "random", "random_integer"
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
    public static double easeInBackOut(double start, double end, double zeroToOne) {
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
    public static double inverseLerp(double start, double end, double zeroToOne) {
        return lerp(start, end, 1 - zeroToOne);
    }

    /// Lerp from start to end via zeroToOne
    public static double lerp(double start, double end, double zeroToOne) {
        //todo test me
        zeroToOne = clamp(zeroToOne, 0, 1);
        return start * zeroToOne + end * (1D - zeroToOne);
    }

    /// Lerp the shortest direction around a circle from start degrees to end degrees via zeroToOne
    public static double lerprotate(double start, double end, double zeroToOne) {
        //todo test me
        zeroToOne = clamp(zeroToOne, 0, 1);
        double diff = end - start;
        if (diff > 180) diff -= 360;
        else if (diff < -180) diff += 360;
        return start + diff * zeroToOne;
    }

    /// Natural logarithm of value
    public static double ln(double value) {
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
        return Math.pow(base, exponent);
    }

    /// Random value between low (inclusive) and high (exclusive)
    ///
    /// Note: The original molang spec says that the range is inclusive, but this high end is exclusive.
    public static double random(double low, double high) {
        return ThreadLocalRandom.current().nextDouble(low, high);
    }

    /// Random integer value between low and high (inclusive)
    public static double randomInteger(double low, double high) {
        return ThreadLocalRandom.current().nextInt((int) low, (int) high + 1);
    }

    /// Round value to nearest integral number
    public static double round(double value) {
        return Math.round(value);
    }

    /// Returns 1 if value is positive, -1 otherwise
    public static double sign(double value) {
        return value > 0 ? 1 : -1;
    }

    /// Sine (in degrees) of value
    public static double sin(double value) {
        return Math.sin(Math.toRadians(value));
    }

    /// Square root of value
    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    /// Round value towards zero
    public static double trunc(double value) {
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }

    // Gross glue below

    private static final class HolderImpl implements MolangValue.Holder {
        private static final MolangValue.Function ABS = (rawArgs) -> {
            double[] args = checkArgs("abs", rawArgs, 1);
            return new MolangValue.Num(abs(args[0]));
        };
        private static final MolangValue.Function ACOS = (rawArgs) -> {
            double[] args = checkArgs("acos", rawArgs, 1);
            return new MolangValue.Num(acos(args[0]));
        };
        private static final MolangValue.Function ASIN = (rawArgs) -> {
            double[] args = checkArgs("asin", rawArgs, 1);
            return new MolangValue.Num(asin(args[0]));
        };
        private static final MolangValue.Function ATAN = (rawArgs) -> {
            double[] args = checkArgs("atan", rawArgs, 1);
            return new MolangValue.Num(atan(args[0]));
        };
        private static final MolangValue.Function ATAN2 = (rawArgs) -> {
            double[] args = checkArgs("atan2", rawArgs, 2);
            return new MolangValue.Num(atan2(args[0], args[1]));
        };
        private static final MolangValue.Function CEIL = (rawArgs) -> {
            double[] args = checkArgs("ceil", rawArgs, 1);
            return new MolangValue.Num(ceil(args[0]));
        };
        private static final MolangValue.Function CLAMP = (rawArgs) -> {
            double[] args = checkArgs("clamp", rawArgs, 3);
            return new MolangValue.Num(clamp(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function COPY_SIGN = (rawArgs) -> {
            double[] args = checkArgs("copy_sign", rawArgs, 2);
            return new MolangValue.Num(copySign(args[0], args[1]));
        };
        private static final MolangValue.Function COS = (rawArgs) -> {
            double[] args = checkArgs("cos", rawArgs, 1);
            return new MolangValue.Num(cos(args[0]));
        };
        private static final MolangValue.Function DIE_ROLL = (rawArgs) -> {
            double[] args = checkArgs("die_roll", rawArgs, 3);
            return new MolangValue.Num(dieRoll(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function DIE_ROLL_INTEGER = (rawArgs) -> {
            double[] args = checkArgs("die_roll_integer", rawArgs, 3);
            return new MolangValue.Num(dieRollInteger(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_BACK = (rawArgs) -> {
            double[] args = checkArgs("ease_in_back", rawArgs, 3);
            return new MolangValue.Num(easeInBack(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_BOUNCE = (rawArgs) -> {
            double[] args = checkArgs("ease_in_bounce", rawArgs, 3);
            return new MolangValue.Num(easeInBounce(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_CIRC = (rawArgs) -> {
            double[] args = checkArgs("ease_in_circ", rawArgs, 3);
            return new MolangValue.Num(easeInCirc(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_CUBIC = (rawArgs) -> {
            double[] args = checkArgs("ease_in_cubic", rawArgs, 3);
            return new MolangValue.Num(easeInCubic(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_ELASTIC = (rawArgs) -> {
            double[] args = checkArgs("ease_in_elastic", rawArgs, 3);
            return new MolangValue.Num(easeInElastic(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_EXPO = (rawArgs) -> {
            double[] args = checkArgs("ease_in_expo", rawArgs, 3);
            return new MolangValue.Num(easeInExpo(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_BACK_OUT = (rawArgs) -> {
            double[] args = checkArgs("ease_in_back_out", rawArgs, 3);
            return new MolangValue.Num(easeInBackOut(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_BOUNCE = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_bounce", rawArgs, 3);
            return new MolangValue.Num(easeInOutBounce(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_CIRC = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_circ", rawArgs, 3);
            return new MolangValue.Num(easeInOutCirc(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_CUBIC = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_cubic", rawArgs, 3);
            return new MolangValue.Num(easeInOutCubic(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_ELASTIC = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_elastic", rawArgs, 3);
            return new MolangValue.Num(easeInOutElastic(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_EXPO = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_expo", rawArgs, 3);
            return new MolangValue.Num(easeInOutExpo(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_QUAD = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_quad", rawArgs, 3);
            return new MolangValue.Num(easeInOutQuad(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_QUART = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_quart", rawArgs, 3);
            return new MolangValue.Num(easeInOutQuart(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_QUINT = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_quint", rawArgs, 3);
            return new MolangValue.Num(easeInOutQuint(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_OUT_SINE = (rawArgs) -> {
            double[] args = checkArgs("ease_in_out_sine", rawArgs, 3);
            return new MolangValue.Num(easeInOutSine(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_QUAD = (rawArgs) -> {
            double[] args = checkArgs("ease_in_quad", rawArgs, 3);
            return new MolangValue.Num(easeInQuad(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_QUART = (rawArgs) -> {
            double[] args = checkArgs("ease_in_quart", rawArgs, 3);
            return new MolangValue.Num(easeInQuart(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_QUINT = (rawArgs) -> {
            double[] args = checkArgs("ease_in_quint", rawArgs, 3);
            return new MolangValue.Num(easeInQuint(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_IN_SINE = (rawArgs) -> {
            double[] args = checkArgs("ease_in_sine", rawArgs, 3);
            return new MolangValue.Num(easeInSine(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_BACK = (rawArgs) -> {
            double[] args = checkArgs("ease_out_back", rawArgs, 3);
            return new MolangValue.Num(easeOutBack(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_BOUNCE = (rawArgs) -> {
            double[] args = checkArgs("ease_out_bounce", rawArgs, 3);
            return new MolangValue.Num(easeOutBounce(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_CIRC = (rawArgs) -> {
            double[] args = checkArgs("ease_out_circ", rawArgs, 3);
            return new MolangValue.Num(easeOutCirc(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_CUBIC = (rawArgs) -> {
            double[] args = checkArgs("ease_out_cubic", rawArgs, 3);
            return new MolangValue.Num(easeOutCubic(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_ELASTIC = (rawArgs) -> {
            double[] args = checkArgs("ease_out_elastic", rawArgs, 3);
            return new MolangValue.Num(easeOutElastic(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_EXPO = (rawArgs) -> {
            double[] args = checkArgs("ease_out_expo", rawArgs, 3);
            return new MolangValue.Num(easeOutExpo(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_QUAD = (rawArgs) -> {
            double[] args = checkArgs("ease_out_quad", rawArgs, 3);
            return new MolangValue.Num(easeOutQuad(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_QUART = (rawArgs) -> {
            double[] args = checkArgs("ease_out_quart", rawArgs, 3);
            return new MolangValue.Num(easeOutQuart(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_QUINT = (rawArgs) -> {
            double[] args = checkArgs("ease_out_quint", rawArgs, 3);
            return new MolangValue.Num(easeOutQuint(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EASE_OUT_SINE = (rawArgs) -> {
            double[] args = checkArgs("ease_out_sine", rawArgs, 3);
            return new MolangValue.Num(easeOutSine(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function EXP = (rawArgs) -> {
            double[] args = checkArgs("exp", rawArgs, 1);
            return new MolangValue.Num(exp(args[0]));
        };
        private static final MolangValue.Function FLOOR = (rawArgs) -> {
            double[] args = checkArgs("floor", rawArgs, 1);
            return new MolangValue.Num(floor(args[0]));
        };
        private static final MolangValue.Function HERMITE_BLEND = (rawArgs) -> {
            double[] args = checkArgs("hermite_blend", rawArgs, 1);
            return new MolangValue.Num(hermiteBlend(args[0]));
        };
        private static final MolangValue.Function INVERSE_LERP = (rawArgs) -> {
            double[] args = checkArgs("inverse_lerp", rawArgs, 3);
            return new MolangValue.Num(inverseLerp(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function LERP = (rawArgs) -> {
            double[] args = checkArgs("lerp", rawArgs, 3);
            return new MolangValue.Num(lerp(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function LERP_ROTATE = (rawArgs) -> {
            double[] args = checkArgs("lerp_rotate", rawArgs, 3);
            return new MolangValue.Num(lerprotate(args[0], args[1], args[2]));
        };
        private static final MolangValue.Function LN = (rawArgs) -> {
            double[] args = checkArgs("ln", rawArgs, 1);
            return new MolangValue.Num(ln(args[0]));
        };
        private static final MolangValue.Function MAX = (rawArgs) -> {
            double[] args = checkArgs("max", rawArgs, 2);
            return new MolangValue.Num(max(args[0], args[1]));
        };
        private static final MolangValue.Function MIN = (rawArgs) -> {
            double[] args = checkArgs("min", rawArgs, 2);
            return new MolangValue.Num(min(args[0], args[1]));
        };
        private static final MolangValue.Function MIN_ANGLE = (rawArgs) -> {
            double[] args = checkArgs("min_angle", rawArgs, 1);
            return new MolangValue.Num(minAngle(args[0]));
        };
        private static final MolangValue.Function MOD = (rawArgs) -> {
            double[] args = checkArgs("mod", rawArgs, 2);
            return new MolangValue.Num(mod(args[0], args[1]));
        };
        private static final MolangValue.Function PI = (rawArgs) -> {
            checkArgs("pi", rawArgs, 0);
            return new MolangValue.Num(pi());
        };
        private static final MolangValue.Function POW = (rawArgs) -> {
            double[] args = checkArgs("pow", rawArgs, 2);
            return new MolangValue.Num(pow(args[0], args[1]));
        };
        private static final MolangValue.Function RANDOM = (rawArgs) -> {
            double[] args = checkArgs("random", rawArgs, 2);
            return new MolangValue.Num(random(args[0], args[1]));
        };
        private static final MolangValue.Function RANDOM_INTEGER = (rawArgs) -> {
            double[] args = checkArgs("random_integer", rawArgs, 2);
            return new MolangValue.Num(randomInteger(args[0], args[1]));
        };
        private static final MolangValue.Function ROUND = (rawArgs) -> {
            double[] args = checkArgs("round", rawArgs, 1);
            return new MolangValue.Num(round(args[0]));
        };
        private static final MolangValue.Function SIGN = (rawArgs) -> {
            double[] args = checkArgs("sign", rawArgs, 1);
            return new MolangValue.Num(sign(args[0]));
        };
        private static final MolangValue.Function SIN = (rawArgs) -> {
            double[] args = checkArgs("sin", rawArgs, 1);
            return new MolangValue.Num(sin(args[0]));
        };
        private static final MolangValue.Function SQRT = (rawArgs) -> {
            double[] args = checkArgs("sqrt", rawArgs, 1);
            return new MolangValue.Num(sqrt(args[0]));
        };
        private static final MolangValue.Function TRUNC = (rawArgs) -> {
            double[] args = checkArgs("trunc", rawArgs, 1);
            return new MolangValue.Num(trunc(args[0]));
        };

        @Override
        public @NotNull MolangValue get(@NotNull String field) {
            return switch (field) {
                case "abs" -> ABS;
                case "acos" -> ACOS;
                case "asin" -> ASIN;
                case "atan" -> ATAN;
                case "atan2" -> ATAN2;
                case "ceil" -> CEIL;
                case "clamp" -> CLAMP;
                case "copy_sign" -> COPY_SIGN;
                case "cos" -> COS;
                case "die_roll" -> DIE_ROLL;
                case "die_roll_integer" -> DIE_ROLL_INTEGER;
                case "ease_in_back" -> EASE_IN_BACK;
                case "ease_in_bounce" -> EASE_IN_BOUNCE;
                case "ease_in_circ" -> EASE_IN_CIRC;
                case "ease_in_cubic" -> EASE_IN_CUBIC;
                case "ease_in_elastic" -> EASE_IN_ELASTIC;
                case "ease_in_expo" -> EASE_IN_EXPO;
                case "ease_in_back_out" -> EASE_IN_BACK_OUT;
                case "ease_in_out_bounce" -> EASE_IN_OUT_BOUNCE;
                case "ease_in_out_circ" -> EASE_IN_OUT_CIRC;
                case "ease_in_out_cubic" -> EASE_IN_OUT_CUBIC;
                case "ease_in_out_elastic" -> EASE_IN_OUT_ELASTIC;
                case "ease_in_out_expo" -> EASE_IN_OUT_EXPO;
                case "ease_in_out_quad" -> EASE_IN_OUT_QUAD;
                case "ease_in_out_quart" -> EASE_IN_OUT_QUART;
                case "ease_in_out_quint" -> EASE_IN_OUT_QUINT;
                case "ease_in_out_sine" -> EASE_IN_OUT_SINE;
                case "ease_in_quad" -> EASE_IN_QUAD;
                case "ease_in_quart" -> EASE_IN_QUART;
                case "ease_in_quint" -> EASE_IN_QUINT;
                case "ease_in_sine" -> EASE_IN_SINE;
                case "ease_out_back" -> EASE_OUT_BACK;
                case "ease_out_bounce" -> EASE_OUT_BOUNCE;
                case "ease_out_circ" -> EASE_OUT_CIRC;
                case "ease_out_elastic" -> EASE_OUT_ELASTIC;
                case "ease_out_expo" -> EASE_OUT_EXPO;
                case "ease_out_quad" -> EASE_OUT_QUAD;
                case "ease_out_quart" -> EASE_OUT_QUART;
                case "ease_out_quint" -> EASE_OUT_QUINT;
                case "ease_out_sine" -> EASE_OUT_SINE;
                case "exp" -> EXP;
                case "floor" -> FLOOR;
                case "hermite_blend" -> HERMITE_BLEND;
                case "inverse_lerp" -> INVERSE_LERP;
                case "lerp" -> LERP;
                case "lerprotate" -> LERP_ROTATE;
                case "ln" -> LN;
                case "max" -> MAX;
                case "min" -> MIN;
                case "min_angle" -> MIN_ANGLE;
                case "mod" -> MOD;
                case "pi" -> PI;
                case "pow" -> POW;
                case "random" -> RANDOM;
                case "random_integer" -> RANDOM_INTEGER;
                case "round" -> ROUND;
                case "sign" -> SIGN;
                case "sin" -> SIN;
                case "sqrt" -> SQRT;
                case "trunc" -> TRUNC;
                default -> NIL;
            };
        }

        private static double[] checkArgs(@NotNull String name, @NotNull List<MolangValue> args, int expected) {
            if (args.size() != expected)
                throw new MolangContentException("Expected %d arguments got %d for %s".formatted(expected, args.size(), name));
            double[] result = new double[expected];
            for (int i = 0; i < expected; i++) {
                // TODO: this needs to generate a content error...
                result[i] = args.get(i) instanceof MolangValue.Num(double value) ? value : 0.0;
            }
            return result;
        }
    }
}
