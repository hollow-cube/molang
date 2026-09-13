package net.hollowcube.molang;

import net.hollowcube.molang.runtime.MolangContentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestMolangEnvironment {

    /// The shape of what multipart evaluates keyframes with: the time within the animation, and the
    /// entity it plays on.
    record Keyframe(double animTime, Entity entity) {}

    record Entity(boolean onGround, String mainHand, double yawChange) {}

    static final MolangEnvironment<Keyframe> KEYFRAMES = MolangEnvironment.<Keyframe>builder()
        .query(
            q -> q.number("anim_time", Keyframe::animTime)
                .number("life_time", Keyframe::animTime)
                .bool("is_on_ground", k -> k.entity().onGround())
                .stringFunction(
                    "is_item_equipped",
                    (k, item) -> k.entity().mainHand().equals(item) ? 1 : 0
                )
                .function("lerp_to", (k, target) -> k.animTime() * target)
                .namespace(
                    "rider",
                    r -> r.function("yaw_change", (k, scale) -> k.entity().yawChange() * scale)
                )
        )
        .context(c -> c.constant("scale", 16))
        .global(g -> g.pure("remap", (a, b, t) -> a + (b - a) * t))
        .build();

    @Test
    public void testKeyframeQueries() {
        var state = new MolangState();
        var ctx = new Keyframe(0.5, new Entity(true, "minecraft:stick", 3));

        assertEquals(45, eval(state, ctx, "math.sin(q.anim_time * 180) * 45"), 1e-9);
        assertEquals(1, eval(state, ctx, "query.is_on_ground"));
        assertEquals(1, eval(state, ctx, "Q.Is_On_Ground()"));
        assertEquals(1, eval(state, ctx, "q.is_item_equipped('minecraft:stick')"));
        assertEquals(0, eval(state, ctx, "q.is_item_equipped('minecraft:Stick')"));
        assertEquals(5, eval(state, ctx, "q.lerp_to(10)"));
        assertEquals(-30, eval(state, ctx, "q.rider.yaw_change(-10)"));
        assertEquals(
            12,
            eval(state, ctx, "c.scale / 2 + context.scale - 16 + remap(0, 8, q.anim_time)"),
            1e-9
        );
    }

    @Test
    public void testConstantsAndPureFunctionsFold() {
        assertEquals(
            12.0,
            KEYFRAMES.compile("remap(0, 8, 0.5) + c.scale / 2").analysis().constant().orElseThrow()
        );
        assertEquals(
            true,
            KEYFRAMES.compile("remap(0, 8, q.anim_time)").analysis().constant().isEmpty()
        );
    }

    @Test
    public void testUnresolvedNames() {
        var program = KEYFRAMES.compile(
            "q.anim_tme + (Q.Missing ?? 1) + math.sine(1) + nope.x + q.rider.yaw_change(1)"
        );
        assertEquals(
            Set.of("query.anim_tme", "query.missing", "math.sine", "nope.x"),
            program.analysis().unresolvedNames()
        );

        var state = new MolangState();
        var ctx = new Keyframe(0, new Entity(false, "", 1));
        assertEquals(3, KEYFRAMES.compile("q.typo ?? 3").eval(state, ctx));
        assertEquals(List.of(), state.getErrors());
    }

    @Test
    public void testWrongUsesAreContentErrors() {
        var state = new MolangState();
        var ctx = new Keyframe(0.5, new Entity(true, "stick", 3));
        for (String input : List.of(
            "q.anim_time(1)", // a number is not a function of arguments
            "q.lerp_to", // a function of one argument read with none
            "q.lerp_to(1, 2)",
            "q.is_item_equipped(1)", // a number for a string parameter
            "q.lerp_to('a')", // a string for a number parameter
            "q.rider", // a namespace is not a value
            "q.anim_time.x",
            "remap(1, 2)"
        )) {
            assertEquals(0, KEYFRAMES.compile(input).eval(state, ctx), input);
            assertEquals(false, state.getErrors().isEmpty(), input);
        }
    }

    @Test
    public void testHostExceptionsAreContentErrors() {
        var environment = MolangEnvironment.<Object>builder()
            .query(
                q -> q
                    .function("checked", (ctx, a) -> {
                        throw new MolangContentException("checked: bad input");
                    })
                    .number("broken", ctx -> {
                        throw new IllegalStateException("oops");
                    })
            )
            .build();
        var state = new MolangState();

        assertEquals(1, environment.compile("q.checked(1) + q.broken + 1").eval(state));
        assertEquals(2, state.getErrors().size());
        assertEquals("checked: bad input", state.getErrors().getFirst().message());
    }

    @Test
    public void testDynamicNamespaces() {
        var environment = MolangEnvironment.<String>builder()
            .query(
                q -> q.dynamic(
                    "props",
                    (ctx, name, args) -> switch (name) {
                        case "owner" -> new MolangValue.Str(ctx);
                        case "deep.count" -> new MolangValue.Num(args.size());
                        default -> MolangValue.NULL;
                    }
                )
            )
            .build();
        var state = new MolangState();

        assertEquals(1, environment.compile("q.props.owner == 'Steve'").eval(state, "Steve"));
        assertEquals(2, environment.compile("q.props.deep.count(1, 2)").eval(state, "Steve"));
        assertEquals(7, environment.compile("q.props.missing ?? 7").eval(state, "Steve"));
        assertEquals(List.of(), state.getErrors());
        assertEquals(
            Set.of(),
            environment.compile("q.props.anything").analysis().unresolvedNames()
        );
    }

    @Test
    public void testRegistrationIsValidated() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MolangEnvironment.builder().query(q -> q.constant("a", 1).constant("A", 2))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MolangEnvironment.builder().namespace("math", ns -> {})
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MolangEnvironment.builder().global(g -> g.constant("V", 1))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MolangEnvironment.builder().query(q -> q.constant("not-a-name", 1))
        );

        // Registering a namespace again adds to it, including through its alias
        var environment = MolangEnvironment.builder()
            .query(q -> q.constant("a", 1))
            .query(q -> q.constant("b", 2))
            .build();
        assertEquals(3, environment.compile("q.a + query.b").analysis().constant().orElseThrow());
    }

    @Test
    public void testGlobalFunctions() {
        var environment = MolangEnvironment.<Object>builder()
            .global(g -> g.variadic("seven", (ctx, args) -> 7 + args.length).constant("answer", 42))
            .build();
        var state = new MolangState();
        assertEquals(7, environment.compile("seven()").eval(state));
        assertEquals(7, environment.compile("seven").eval(state));
        assertEquals(8, environment.compile("seven(1)").eval(state));
        assertEquals(42, environment.compile("answer").eval(state));
        assertEquals(List.of(), state.getErrors());
    }

    /// mapmaker's velocity modifiers and particle emitters expose host values as variables.
    @Test
    public void testHostVariables() {
        record Player(double x, double y) {}
        var environment = MolangEnvironment.<Player>builder()
            .variables(
                v -> v.number("x", Player::x)
                    .number("y", Player::y)
                    .namespace("pos", pos -> pos.number("x", Player::x))
            )
            .build();
        var state = new MolangState();
        var player = new Player(3, 4);

        assertEquals(7, environment.compile("v.x + variable.Y").eval(state, player));
        assertEquals(3, environment.compile("v.pos.x").eval(state, player));
        assertEquals(
            7,
            environment.compile("v.other = 2; v.other + v.x + v.y - 2").eval(state, player)
        );
        assertEquals(List.of(), state.getErrors());
        assertEquals(new MolangValue.Num(2), state.getVariable("other"));

        assertEquals(0, environment.compile("v.x = 5").eval(state, player));
        assertEquals(1, state.getErrors().size());
        assertEquals(3, environment.compile("v.x").eval(state, player));
    }

    /// mapmaker's parkour maps read author-named variables from storage, with unset ones as 0.
    @Test
    public void testDynamicVariables() {
        var environment = MolangEnvironment.<java.util.Map<String, Double>>builder()
            .variables(
                v -> v.dynamic(
                    (storage, name, args) -> new MolangValue.Num(storage.getOrDefault(name, 0.0))
                )
            )
            .build();
        var state = new MolangState();
        var storage = java.util.Map.of("coins", 12.0);

        assertEquals(13, environment.compile("v.coins + v.unset + 1").eval(state, storage));
        assertEquals(List.of(), state.getErrors());
        assertEquals(Set.of(), environment.compile("v.anything").analysis().unresolvedNames());
        assertEquals(0, environment.compile("v.coins = 1").eval(state, storage));
        assertEquals(1, state.getErrors().size());
        assertEquals(2, environment.compile("t.x = 2; t.x").eval(state, storage));
    }

    @Test
    public void testNamespaceFallback() {
        var environment = MolangEnvironment.<Object>builder()
            .query(
                q -> q.constant("known", 1)
                    .dynamic(
                        (
                            ctx,
                            name,
                            args
                        ) -> name.startsWith("is_") ? new MolangValue.Num(1) : MolangValue.NULL
                    )
            )
            .build();
        var state = new MolangState();
        assertEquals(
            3,
            environment.compile("q.known + q.is_anything + (q.other ?? 1)").eval(state)
        );
        assertEquals(List.of(), state.getErrors());
    }

    @Test
    public void testOptionalNumbers() {
        var environment = MolangEnvironment.<Double>builder()
            .query(q -> q.optionalNumber("best_playtime", best -> best))
            .build();
        var state = new MolangState();
        assertEquals(5, environment.compile("q.best_playtime ?? 5").eval(state, null));
        assertEquals(7, environment.compile("q.best_playtime ?? 5").eval(state, 7.0));
        assertEquals(List.of(), state.getErrors());
    }

    @Test
    public void testEvalBool() {
        var state = new MolangState();
        assertEquals(true, MolangProgram.compile("2 > 1").evalBool(state));
        assertEquals(
            false,
            KEYFRAMES.compile("q.is_on_ground")
                .evalBool(state, new Keyframe(0, new Entity(false, "", 0)))
        );
    }

    @Test
    public void testAnalysis() {
        var analysis = KEYFRAMES
            .compile(
                "t.a = q.anim_time; v.out = t.a * c.scale + remap(0, 1, Query.Life_Time) + math.sin(t.a)"
            )
            .analysis();
        assertEquals(Set.of("query.anim_time", "query.life_time"), analysis.hostNames());
        assertEquals(Set.of("variable.out"), analysis.variablesWritten());
        assertEquals(Set.of(), analysis.variablesRead());
        assertEquals(true, analysis.usesTemps());
        assertEquals(false, analysis.random());
        assertEquals(true, analysis.dependsOnlyOn(Set.of("query.anim_time", "query.life_time")));
        assertEquals(false, analysis.dependsOnlyOn(Set.of("query.anim_time")));

        var rider = KEYFRAMES.compile("q.rider.yaw_change(2) + v.speed + math.random(0, 1)")
            .analysis();
        assertEquals(Set.of("query.rider.yaw_change"), rider.hostNames());
        assertEquals(Set.of("variable.speed"), rider.variablesRead());
        assertEquals(true, rider.random());
        assertEquals(false, rider.dependsOnlyOn(Set.of("query.rider.yaw_change")));

        var dynamic = MolangEnvironment.<Object>builder()
            .query(q -> q.dynamic("props", (ctx, name, args) -> MolangValue.NULL))
            .variables(v -> v.dynamic((ctx, name, args) -> MolangValue.NULL))
            .build()
            .compile("q.props.owner.name + v.coins + q.props")
            .analysis();
        assertEquals(
            Set.of("query.props.owner.name", "variable.coins", "query.props"),
            dynamic.dynamicNames()
        );
        assertEquals(Set.of(), dynamic.hostNames());
        assertEquals(Set.of(), dynamic.unresolvedNames());

        var constant = KEYFRAMES.compile("c.scale * 2").analysis();
        assertEquals(32.0, constant.constant().orElseThrow());
        assertEquals(Set.of(), constant.hostNames());
        assertEquals(true, constant.dependsOnlyOn(Set.of()));
    }

    @Test
    public void testCompileFromSource() {
        assertEquals(
            2.0,
            MolangEnvironment.empty().compile("1 + 1").analysis().constant().orElseThrow()
        );
        assertThrows(MolangParseException.class, () -> MolangEnvironment.empty().compile("1 +"));
    }

    private static double eval(MolangState state, Keyframe ctx, String source) {
        double result = KEYFRAMES.compile(source).eval(state, ctx);
        assertEquals(List.of(), state.getErrors(), source);
        return result;
    }
}
