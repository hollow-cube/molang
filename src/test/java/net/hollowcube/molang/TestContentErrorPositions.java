package net.hollowcube.molang;

import net.hollowcube.molang.runtime.ContentError;
import net.hollowcube.molang.runtime.MolangContentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Content errors point at the expression that caused them.
public class TestContentErrorPositions {
    static final MolangEnvironment<Object> ENV = MolangEnvironment.builder()
        .query(
            q -> q.function("half", (ctx, a) -> a / 2)
                .function("checked", (ctx, a) -> {
                    throw new MolangContentException("checked: bad input");
                })
        )
        .build();

    @Test
    public void testDivisionByZeroIsAtTheOperator() {
        assertEquals(List.of("1:3"), locations("1 / 0"));
    }

    @Test
    public void testNonNumberIsAtTheOperand() {
        assertEquals(List.of("2:7"), locations("v.a = 1;\nv.b = 'x' + 1"));
        assertEquals(List.of("1:8"), locations("return 'str'"));
    }

    @Test
    public void testCallErrorsAreAtTheCallOrArgument() {
        assertEquals(List.of("1:5"), locations("1 + math.sqrt(-1)"));
        assertEquals(List.of("1:8"), locations("q.half('a')"));
        assertEquals(List.of("1:1"), locations("q.checked(1)"));
        assertEquals(List.of("1:1"), locations("q.half(1, 2)"));
    }

    @Test
    public void testOtherErrors() {
        assertEquals("1:3", locations("1;break").getFirst());
        assertEquals(List.of("1:5"), locations("1 + q.half"));
        assertEquals("1:1", locations("loop(1)").getFirst());
        assertEquals(List.of("1:1"), locations(""));
    }

    @Test
    public void testMessagesAndLocations() {
        var error = errors("1 +\n  1 / 0").getFirst();
        assertEquals("Division by zero", error.message());
        assertEquals("1 +\n  1 / 0", error.source());
        assertEquals(8, error.position());
        assertEquals(2, error.line());
        assertEquals(5, error.column());
        assertEquals("Division by zero at line 2, column 5", error.toString());
    }

    @Test
    public void testExpressionsWithoutSourceOrPositions() {
        // Only possible internally, eg for expressions built by hand, which have no positions
        var state = new MolangState();
        var division = new MolangExpr.Binary(
            MolangExpr.Binary.Op.DIV,
            new MolangExpr.Num(1),
            new MolangExpr.Num(0)
        );
        MolangProgram.compile(ENV, division, null, true).eval(state);
        var error = state.getErrors().getFirst();
        assertEquals(-1, error.position());
        assertEquals(-1, error.line());
        assertEquals("Division by zero", error.toString());
    }

    private static List<ContentError> errors(String source) {
        var state = new MolangState();
        ENV.compile(source).eval(state);
        return state.getErrors();
    }

    /// Where each error is, as `line:column`, so tests don't depend on message wording.
    private static List<String> locations(String source) {
        return errors(source).stream().map(error -> error.line() + ":" + error.column()).toList();
    }
}
