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

    /**
     * Absolute value of value
     */
    public static double abs(double value) {
        return Math.abs(value);
    }

    /**
     * arccos of value
     */
    public static double acos(double value) {
        assertion(value >= -1 && value <= 1, "acos: value must be in the range [-1, 1]");
        return Math.toDegrees(Math.acos(value));
    }

    /**
     * arcsin of value
     */
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

    /**
     * arctan of y/x. NOTE: the order of arguments!
     */
    public static double atan2(double y, double x) {
        return Math.toDegrees(Math.atan2(y, x));
    }

    /**
     * Round value up to nearest integral number
     */
    public static double ceil(double value) {
        return Math.ceil(value);
    }

    /**
     * Clamp value to between min and max inclusive
     */
    public static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * Cosine (in degrees) of value
     */
    public static double cos(double value) {
        return Math.cos(Math.toRadians(value));
    }

    /**
     * Returns the sum of 'num' random numbers, each with a value from low to high. Note: the generated random numbers are not integers like normal dice. For that, use math.die_roll_integer.
     */
    public static double dieRoll(double num, double low, double high) {
        double total = 0;
        for (int i = 0; i < Math.clamp(num, 0, DIE_ROLL_ITERATION_LIMIT); i++)
            total += random(low, high);
        return total;
    }

    /**
     * Returns the sum of 'num' random integer numbers, each with a value from low to high. Note: the generated random numbers are integers like normal dice.
     */
    public static double dieRollInteger(double num, double low, double high) {
        double total = 0;
        for (int i = 0; i < Math.clamp(num, 0, DIE_ROLL_ITERATION_LIMIT); i++)
            total += randomInteger(low, high);
        return total;
    }

    /**
     * Calculates e to the value 'nth' power
     */
    public static double exp(double value) {
        return Math.exp(value);
    }

    /**
     * Round value down to nearest integral number
     */
    public static double floor(double value) {
        return Math.floor(value);
    }

    /**
     * Useful for simple smooth curve interpolation using one of the Hermite Basis functions: 3t^2 - 2t^3. Note that while any valid float is a valid input, this function works best in the range [0,1].
     */
    public static double hermiteBlend(double value) {
        //todo: implement me
        throw new RuntimeException("hermite_blend not implemented");
    }

    /**
     * Lerp from start to end via zeroToOne
     */
    public static double lerp(double start, double end, double zeroToOne) {
        //todo test me
        zeroToOne = clamp(zeroToOne, 0, 1);
        return start * zeroToOne + end * (1D - zeroToOne);
    }

    /**
     * Lerp the shortest direction around a circle from start degrees to end degrees via zeroToOne
     */
    public static double lerprotate(double start, double end, double zeroToOne) {
        //todo test me
        zeroToOne = clamp(zeroToOne, 0, 1);
        double diff = end - start;
        if (diff > 180) diff -= 360;
        else if (diff < -180) diff += 360;
        return start + diff * zeroToOne;
    }

    /**
     * Natural logarithm of value
     */
    public static double ln(double value) {
        return Math.log(value);
    }

    /**
     * Return highest value of A or B
     */
    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    /**
     * Return lowest value of A or B
     */
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    /**
     * Minimize angle magnitude (in degrees) into the range [-180, 180)
     */
    public static double minAngle(double value) {
        //todo: implement me
        throw new RuntimeException("hermite_blend not implemented");
    }

    /**
     * Return the remainder of value / denominator
     */
    public static double mod(double value, double denominator) {
        assertion(denominator != 0, "mod: denominator cannot be zero");
        return value % denominator;
    }

    /**
     * Returns the float representation of the constant pi.
     */
    public static double pi() {
        return Math.PI;
    }

    /**
     * Elevates base to the exponent'th power
     */
    public static double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    /**
     * Random value between low (inclusive) and high (exclusive)
     * <p>
     * Note: The original molang spec says that the range is inclusive, but this high end is exclusive.
     */
    public static double random(double low, double high) {
        return ThreadLocalRandom.current().nextDouble(low, high);
    }

    /**
     * Random integer value between low and high (inclusive)
     */
    public static double randomInteger(double low, double high) {
        return ThreadLocalRandom.current().nextInt((int) low, (int) high + 1);
    }

    /**
     * Round value to nearest integral number
     */
    public static double round(double value) {
        return Math.round(value);
    }

    /**
     * Sine (in degrees) of value
     */
    public static double sin(double value) {
        return Math.sin(Math.toRadians(value));
    }

    /**
     * Square root of value
     */
    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    /**
     * Round value towards zero
     */
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
                case "cos" -> COS;
                case "die_roll" -> DIE_ROLL;
                case "die_roll_integer" -> DIE_ROLL_INTEGER;
                case "exp" -> EXP;
                case "floor" -> FLOOR;
                case "hermite_blend" -> HERMITE_BLEND;
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
