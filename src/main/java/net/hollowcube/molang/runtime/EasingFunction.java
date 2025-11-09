package net.hollowcube.molang.runtime;

public interface EasingFunction {

    // from https://github.com/ai/easings.net/blob/master/src/easings/easingsFunctions.ts

    EasingFunction easeInQuad = (x) -> x * x;
    EasingFunction easeOutQuad = (x) -> 1 - (1 - x) * (1 - x);
    EasingFunction easeInOutQuad = (x) -> x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
    EasingFunction easeInCubic = (x) -> x * x * x;
    EasingFunction easeOutCubic = (x) -> 1 - Math.pow(1 - x, 3);
    EasingFunction easeInOutCubic = (x) -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    EasingFunction easeInQuart = (x) -> x * x * x * x;
    EasingFunction easeOutQuart = (x) -> 1 - Math.pow(1 - x, 4);
    EasingFunction easeInOutQuart = (x) -> x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
    EasingFunction easeInQuint = (x) -> x * x * x * x * x;
    EasingFunction easeOutQuint = (x) -> 1 - Math.pow(1 - x, 5);
    EasingFunction easeInOutQuint = (x) -> x < 0.5 ? 16 * x * x * x * x * x : 1 - Math.pow(-2 * x + 2, 5) / 2;
    EasingFunction easeInSine = (x) -> 1 - Math.cos((x * Math.PI) / 2);
    EasingFunction easeOutSine = (x) -> Math.sin((x * Math.PI) / 2);
    EasingFunction easeInOutSine = (x) -> -(Math.cos(Math.PI * x) - 1) / 2;
    EasingFunction easeInExpo = (x) -> x == 0 ? 0 : Math.pow(2, 10 * x - 10);
    EasingFunction easeOutExpo = (x) -> x == 1 ? 1 : 1 - Math.pow(2, -10 * x);
    EasingFunction easeInOutExpo = (x) -> x == 0 ? 0 : x == 1 ? 1 : x < 0.5 ? Math.pow(2, 20 * x - 10) / 2 : (2 - Math.pow(2, -20 * x + 10)) / 2;
    EasingFunction easeInCirc = (x) -> 1 - Math.sqrt(1 - Math.pow(x, 2));
    EasingFunction easeOutCirc = (x) -> Math.sqrt(1 - Math.pow(x - 1, 2));
    EasingFunction easeInOutCirc = (x) -> x < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) / 2;
    EasingFunction easeInBack = (x) -> 3.5949095 * x * x * x - 1.70158 * x * x;
    EasingFunction easeOutBack = (x) -> 1 + 3.5949095 * Math.pow(x - 1, 3) + 1.70158 * Math.pow(x - 1, 2);
    EasingFunction easeInOutBack = (x) -> x < 0.5 ? (Math.pow(2 * x, 2) * ((2.5949095 + 1) * 2 * x - 2.5949095)) / 2 : (Math.pow(2 * x - 2, 2) * ((2.5949095 + 1) * (x * 2 - 2) + 2.5949095) + 2) / 2;
    EasingFunction easeInElastic = (x) -> x == 0 ? 0 : x == 1 ? 1 : -Math.pow(2, 10 * x - 10) * Math.sin((x * 10 - 10.75) * 2.0943951023931953);
    EasingFunction easeOutElastic = (x) -> x == 0 ? 0 : x == 1 ? 1 : Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * 2.0943951023931953) + 1;
    EasingFunction easeInOutElastic = (x) -> x == 0 ? 0 : x == 1 ? 1 : x < 0.5 ? -(Math.pow(2, 20 * x - 10) * Math.sin((20 * x - 11.125) * 1.3962634015954636)) / 2 : (Math.pow(2, -20 * x + 10) * Math.sin((20 * x - 11.125) * 1.3962634015954636)) / 2 + 1;
    EasingFunction easeInBounce = (x) -> 1 - bounceOut(1 - x);
    EasingFunction easeOutBounce = EasingFunction::bounceOut;
    EasingFunction easeInOutBounce = (x) -> x < 0.5 ? (1 - bounceOut(1 - 2 * x)) / 2 : (1 + bounceOut(2 * x - 1)) / 2;

    double apply(double t);

    default float apply(float t) {
        return (float) this.apply((double) t);
    }

    private static double bounceOut(double x) {
        double n1 = 7.5625;
        double d1 = 2.75;

        if (x < 1 / d1) {
            return n1 * x * x;
        } else if (x < 2 / d1) {
            return n1 * (x -= 1.5 / d1) * x + 0.75;
        } else if (x < 2.5 / d1) {
            return n1 * (x -= 2.25 / d1) * x + 0.9375;
        } else {
            return n1 * (x -= 2.625 / d1) * x + 0.984375;
        }
    }
}
