package net.hollowcube.molang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class MolangParser {
    private final MolangLexer lexer;

    public MolangParser(@NotNull String source) {
        this(source, false);
    }

    public MolangParser(@NotNull String source, boolean multiline) {
        this.lexer = new MolangLexer(source);
        //todo multiline support
    }

    public @NotNull MolangExpr parse() {
        return expr(0);
    }

    private @NotNull MolangExpr expr(int minBindingPower) {
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
                        var trueExpr = expr(0);
                        lexer.expect(MolangLexer.Tok.COLON);
                        var falseExpr = expr(postfixBindingPower);
                        yield new MolangExpr.Ternary(lhs, trueExpr, falseExpr);
                    }
                    case LPAREN -> {
                        // Get argument list
                        List<MolangExpr> args = new ArrayList<>();
                        var next = lexer.peek();

                        if (next != null && next != MolangLexer.Tok.RPAREN) {
                            do {
                                args.add(expr(0));
                                next = lexer.peek();
                            } while (next == MolangLexer.Tok.COMMA && lexer.next() != null);

                            lexer.expect(MolangLexer.Tok.RPAREN);
                            yield new MolangExpr.Call(lhs, args);
                        }
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
                case MEMBER_ACCESS -> {
                    if (!(rhs instanceof MolangExpr.Ident(String value)))
                        throw new IllegalStateException("rhs of member access must be an ident, was " + rhs);
                    yield new MolangExpr.Access(lhs, value);
                }
                default -> new MolangExpr.Binary(op.op, lhs, rhs);
            };
        }

        return lhs;
    }

    /**
     * Parses a possible left side expression.
     */
    private @NotNull MolangExpr lhs() {
        MolangLexer.Tok token = lexer.next();
        if (token == null) throw new IllegalStateException("unexpected end of input");

        return switch (token) {
            case NUMBER -> new MolangExpr.Num(Double.parseDouble(lexer.span()));
            case IDENT -> new MolangExpr.Ident(lexer.span());
            case MINUS -> {
                var rhs = expr(Operator.MINUS.prefixBindingPower());
                yield new MolangExpr.Unary(MolangExpr.Unary.Op.NEGATE, rhs);
            }
            case BANG -> {
                var rhs = expr(Operator.NOT.prefixBindingPower());
                yield new MolangExpr.Unary(MolangExpr.Unary.Op.NOT, rhs);
            }
            case LPAREN -> {
                var expr = expr(0);
                lexer.expect(MolangLexer.Tok.RPAREN);
                yield expr;
            }
            case LBRACE -> {
                List<MolangExpr> exprs = new ArrayList<>();
                MolangLexer.Tok next;
                while ((next = lexer.peek()) != null && next != MolangLexer.Tok.RBRACK) {
                    exprs.add(expr(0));
                    lexer.expect(MolangLexer.Tok.SEMICOLON);
                }
                lexer.expect(MolangLexer.Tok.RBRACK);
                yield new MolangExpr.Block(exprs);
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
            default -> null;
        };
    }

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

        NULL_COALESCE(5, 6, MolangExpr.Binary.Op.NULL_COALESCE),

        LPAREN(30, 30, null);

        private final int lbp;
        private final int rbp;
        private final MolangExpr.Binary.Op op;

        Operator(int lbp, int rbp, MolangExpr.Binary.Op op) {
            this.lbp = lbp;
            this.rbp = rbp;
            this.op = op;
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
                case TERNARY -> 1;
                case LPAREN -> 34;
                default -> -1;
            };
        }
    }
}
