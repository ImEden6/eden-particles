package com.eden.particles.impl.expr;

import com.eden.particles.impl.codec.Codecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code power * 2 + min(spread, 1)}: numbers, input names, {@code + - * / %}, unary minus, parentheses and
 * {@link MathFunction}s. Parts without inputs are folded to constants.
 */
public final class ExprParser {

    private final String text;
    private int pos;

    private ExprParser(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException with the column of the problem */
    public static Expr parse(String text) {
        ExprParser parser = new ExprParser(text);
        parser.skipSpaces();
        if (parser.atEnd()) throw new IllegalArgumentException("empty expression");
        Expr expr = parser.sum();
        parser.skipSpaces();
        if (!parser.atEnd()) throw parser.error("unexpected '" + parser.peek() + "'");
        return expr;
    }

    private Expr sum() {
        Expr left = product();
        while (true) {
            skipSpaces();
            if (atEnd() || (peek() != '+' && peek() != '-')) return left;
            char op = text.charAt(pos++);
            left = binary(op, left, product());
        }
    }

    private Expr product() {
        Expr left = unary();
        while (true) {
            skipSpaces();
            if (atEnd() || (peek() != '*' && peek() != '/' && peek() != '%')) return left;
            char op = text.charAt(pos++);
            left = binary(op, left, unary());
        }
    }

    private Expr unary() {
        skipSpaces();
        if (!atEnd() && peek() == '-') {
            pos++;
            Expr operand = unary();
            return operand instanceof Expr.Const c ? new Expr.Const(-c.value()) : new Expr.Negate(operand);
        }
        return primary();
    }

    private Expr primary() {
        skipSpaces();
        if (atEnd()) throw error("expected a number, input or '('");
        char c = peek();
        if (c == '(') {
            pos++;
            Expr inner = sum();
            expect(')');
            return inner;
        }
        if (Character.isDigit(c) || c == '.') return number();
        if (Character.isLetter(c) || c == '_') return name();
        throw error("unexpected '" + c + "'");
    }

    private Expr number() {
        int start = pos;
        while (!atEnd() && (Character.isDigit(peek()) || peek() == '.')) pos++;
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            pos++;
            if (!atEnd() && (peek() == '+' || peek() == '-')) pos++;
            while (!atEnd() && Character.isDigit(peek())) pos++;
        }
        String literal = text.substring(start, pos);
        try {
            float value = Float.parseFloat(literal);
            if (!Float.isFinite(value)) throw new NumberFormatException();
            return new Expr.Const(value);
        } catch (NumberFormatException e) {
            pos = start;
            throw error("invalid number '" + literal + "'");
        }
    }

    private Expr name() {
        int start = pos;
        while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) pos++;
        String name = text.substring(start, pos);
        skipSpaces();
        if (atEnd() || peek() != '(') return new Expr.Input(name, InputNames.id(name));

        Optional<MathFunction> function = MathFunction.byId(name);
        if (function.isEmpty()) {
            pos = start;
            throw error(Codecs.unknownChoice("function", name, MathFunction.NAMES));
        }
        pos++;
        List<Expr> args = new ArrayList<>();
        skipSpaces();
        if (!atEnd() && peek() == ')') {
            pos++;
        } else {
            args.add(sum());
            while (true) {
                skipSpaces();
                if (!atEnd() && peek() == ',') {
                    pos++;
                    args.add(sum());
                } else {
                    expect(')');
                    break;
                }
            }
        }
        if (!function.get().accepts(args.size())) {
            pos = start;
            throw error(name + "() takes " + function.get().arity() + ", got " + args.size());
        }
        Expr call = new Expr.Call(function.get(), List.copyOf(args));
        return args.stream().allMatch(a -> a instanceof Expr.Const) ? new Expr.Const(call.eval(new float[0])) : call;
    }

    private static Expr binary(char op, Expr left, Expr right) {
        if (left instanceof Expr.Const a && right instanceof Expr.Const b) {
            return new Expr.Const(Expr.Binary.apply(op, a.value(), b.value()));
        }
        return new Expr.Binary(op, left, right);
    }

    private void expect(char c) {
        skipSpaces();
        if (atEnd() || peek() != c) throw error("expected '" + c + "'");
        pos++;
    }

    private void skipSpaces() {
        while (!atEnd() && Character.isWhitespace(peek())) pos++;
    }

    private boolean atEnd() {
        return pos >= text.length();
    }

    private char peek() {
        return text.charAt(pos);
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("in '" + text + "' at column " + (pos + 1) + ": " + message);
    }
}
