package net.hollowcube.molang;

import net.hollowcube.molang.eval.MolangValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MolangParser {
    private final MolangLexer lexer;
    // Whether we are in the then branch of a conditional, where (as in molangjs) the next `:` belongs
    // to the outer conditional, so a nested `a ? b` cannot take an else branch unless it is grouped.
    private boolean inThenBranch = false;

    public MolangParser(String source) {
        this(source, false);
    }

    public MolangParser(String source, boolean multiline) {
        this.lexer = new MolangLexer(source);
        //todo multiline support
    }

    /// Parses a single expression, or a script of `;` separated statements as a [MolangExpr.Block].
    public MolangExpr parse() {
        var statements = statements();
        var trailing = lexer.next();
        if (trailing != null)
            throw new IllegalStateException("unexpected token " + trailing + " after expression");
        return switch (statements.size()) {
            case 0 -> throw new IllegalStateException("unexpected end of input");
            case 1 -> statements.getFirst();
            default -> new MolangExpr.Block(statements);
        };
    }

    /// Parses `;` separated statements up to the end of input or a closing brace. Empty statements
    /// (eg a trailing `;`) are skipped.
    private List<MolangExpr> statements() {
        List<MolangExpr> statements = new ArrayList<>();
        while (true) {
            var next = lexer.peek();
            if (next == null || next == MolangLexer.Tok.RBRACK) break;
            if (next == MolangLexer.Tok.SEMICOLON) {
                lexer.next();
                continue;
            }

            statements.add(expr(0));
            if (lexer.peek() != MolangLexer.Tok.SEMICOLON) break;
        }
        return statements;
    }

    private MolangExpr expr(int minBindingPower) {
        MolangExpr lhs = lhs();

        while (true) {
            Operator op = operator();
            if (op == null) break;

            var postfixBindingPower = op.postfixBindingPower();
            if (postfixBindingPower != -1) {
                if (postfixBindingPower < minBindingPower) break;
                lexer.next(); // Operator token

                lhs = switch (op) {
                    case TERNARY -> {
                        var outerThenBranch = inThenBranch;
                        inThenBranch = true;
                        var trueExpr = expr(CONDITIONAL_BRANCH_BINDING_POWER);
                        inThenBranch = outerThenBranch;

                        // Binary conditional `a ? b` is `a ? b : 0`
                        if (inThenBranch || lexer.peek() != MolangLexer.Tok.COLON)
                            yield new MolangExpr.Ternary(lhs, trueExpr, new MolangExpr.Num(0));
                        lexer.next(); // Colon
                        var falseExpr = expr(CONDITIONAL_BRANCH_BINDING_POWER);
                        yield new MolangExpr.Ternary(lhs, trueExpr, falseExpr);
                    }
                    case LPAREN -> {
                        // Get argument list
                        List<MolangExpr> args = new ArrayList<>();
                        var next = lexer.peek();

                        if (next != null && next != MolangLexer.Tok.RPAREN) {
                            do {
                                args.add(grouped());
                                next = lexer.peek();
                            } while (next == MolangLexer.Tok.COMMA && lexer.next() != null);

                            lexer.expect(MolangLexer.Tok.RPAREN);
                            yield new MolangExpr.Call(lhs, args);
                        }
                        lexer.expect(MolangLexer.Tok.RPAREN);
                        yield lhs;
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };

                continue;
            }

            // Stop if operator left binding power is less than the current min
            if (op.lbp < minBindingPower) break;

            lexer.next(); // Operator token

            // Parse right side expression
            MolangExpr rhs = expr(op.rbp);
            lhs = switch (op) {
                case ASSIGN -> {
                    if (!(lhs instanceof MolangExpr.Access target))
                        throw new IllegalStateException("cannot assign to " + lhs);
                    yield new MolangExpr.Assign(target, rhs);
                }
                case MEMBER_ACCESS -> {
                    if (!(rhs instanceof MolangExpr.Ident(String value)))
                        throw new IllegalStateException(
                            "rhs of member access must be an ident, was " + rhs
                        );
                    yield new MolangExpr.Access(lhs, value);
                }
                default -> new MolangExpr.Binary(op.binaryOp(), lhs, rhs);
            };
        }

        return lhs;
    }

    /// Parses an expression inside brackets or an argument list, where a conditional may take its own `:`.
    private MolangExpr grouped() {
        var outerThenBranch = inThenBranch;
        inThenBranch = false;
        var expr = expr(0);
        inThenBranch = outerThenBranch;
        return expr;
    }

    /**
     * Parses a possible left side expression.
     */
    private MolangExpr lhs() {
        MolangLexer.Tok token = lexer.next();
        if (token == null) throw new IllegalStateException("unexpected end of input");

        return switch (token) {
            case NUMBER -> new MolangExpr.Num(Double.parseDouble(lexer.span()));
            case IDENT -> {
                // Names are case-insensitive, so they are normalized to lowercase
                var name = MolangValue.Holder.normalizeName(lexer.span());
                if (!name.equals("return")) yield new MolangExpr.Ident(name);
                // A bare `return` yields 0
                var next = lexer.peek();
                if (next == null
                    || next == MolangLexer.Tok.SEMICOLON
                    || next == MolangLexer.Tok.RBRACK)
                    yield new MolangExpr.Return(new MolangExpr.Num(0));
                yield new MolangExpr.Return(expr(0));
            }
            case MINUS -> {
                var rhs = expr(Operator.MINUS.prefixBindingPower());
                yield new MolangExpr.Unary(MolangExpr.Unary.Op.NEGATE, rhs);
            }
            case BANG -> {
                var rhs = expr(Operator.NOT.prefixBindingPower());
                yield new MolangExpr.Unary(MolangExpr.Unary.Op.NOT, rhs);
            }
            case LPAREN -> {
                var expr = grouped();
                lexer.expect(MolangLexer.Tok.RPAREN);
                yield expr;
            }
            case LBRACE -> {
                var outerThenBranch = inThenBranch;
                inThenBranch = false;
                var statements = statements();
                inThenBranch = outerThenBranch;
                lexer.expect(MolangLexer.Tok.RBRACK);
                yield new MolangExpr.Block(statements);
            }
            //todo better error handling
            default -> throw new IllegalStateException("unexpected token " + token);
        };
    }

    private @Nullable Operator operator() {
        MolangLexer.Tok token = lexer.peek();
        if (token == null) return null;
        return switch (token) {
            case PLUS -> Operator.PLUS;
            case MINUS -> Operator.MINUS;
            case SLASH -> Operator.DIV;
            case STAR -> Operator.MUL;
            case DOT -> Operator.MEMBER_ACCESS;
            case QUESTION -> Operator.TERNARY;
            case QUESTIONQUESTION -> Operator.NULL_COALESCE;
            case LPAREN -> Operator.LPAREN;
            case GTEQ -> Operator.GTE;
            case GT -> Operator.GE;
            case LTEQ -> Operator.LTE;
            case LT -> Operator.LE;
            case EQEQ -> Operator.EQ;
            case BANGEQ -> Operator.NEQ;
            case AMPAMP -> Operator.AND;
            case BARBAR -> Operator.OR;
            case EQ -> Operator.ASSIGN;
            default -> null;
        };
    }

    // Conditional branches stop before `??` but may contain assignments and further conditionals.
    private static final int CONDITIONAL_BRANCH_BINDING_POWER = 2;

    private enum Operator {
        MEMBER_ACCESS(35, 36, null), // TODO

        NOT(28, 29, null), // Prefix only

        MUL(27, 28, MolangExpr.Binary.Op.MUL),
        DIV(27, 28, MolangExpr.Binary.Op.DIV),

        PLUS(25, 26, MolangExpr.Binary.Op.PLUS),
        MINUS(25, 26, MolangExpr.Binary.Op.MINUS),

        GTE(24, 25, MolangExpr.Binary.Op.GTE),
        GE(24, 25, MolangExpr.Binary.Op.GT),
        LTE(24, 25, MolangExpr.Binary.Op.LTE),
        LE(24, 25, MolangExpr.Binary.Op.LT),

        EQ(23, 24, MolangExpr.Binary.Op.EQ),
        NEQ(23, 24, MolangExpr.Binary.Op.NEQ),

        AND(21, 22, MolangExpr.Binary.Op.AND),
        OR(19, 20, MolangExpr.Binary.Op.OR),

        TERNARY(0, 0, null), // Open of a ternary expression (?), only a postfix operator

        // Binds looser than conditionals, as in molangjs: `a ?? b ? c : d` is `a ?? (b ? c : d)`
        NULL_COALESCE(1, 2, MolangExpr.Binary.Op.NULL_COALESCE),

        ASSIGN(2, 0, null), // Right side is the rest of the expression, eg `v.x = a ? b : c`

        LPAREN(30, 30, null),
        ;

        private final int lbp;
        private final int rbp;
        private final @Nullable MolangExpr.Binary.Op op;

        Operator(int lbp, int rbp, @Nullable MolangExpr.Binary.Op op) {
            this.lbp = lbp;
            this.rbp = rbp;
            this.op = op;
        }

        public MolangExpr.Binary.Op binaryOp() {
            return Objects.requireNonNull(this.op, () -> this.op + " is not a binary operator");
        }

        public int prefixBindingPower() {
            return switch (this) {
                case MINUS -> 30;
                case NOT -> 30;
                default -> -1;
            };
        }

        public int postfixBindingPower() {
            return switch (this) {
                case TERNARY -> 3;
                case LPAREN -> 34;
                default -> -1;
            };
        }
    }
}
