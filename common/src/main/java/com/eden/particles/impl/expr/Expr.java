package com.eden.particles.impl.expr;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A number computed from an effect's inputs. Inputs are read from an array indexed by {@link InputNames} ids; a
 * missing input reads as 0. Results are always finite: division by zero and overflow give 0.
 */
public sealed interface Expr {

    Const ZERO = new Const(0f);
    Const ONE = new Const(1f);

    float eval(float[] inputs);

    void names(Set<String> out);

    /** Source form, fully parenthesized; parses back to an equal expression. */
    String print();

    static Expr constant(float value) {
        return new Const(value);
    }

    record Const(float value) implements Expr {
        @Override
        public float eval(float[] inputs) {
            return value;
        }

        @Override
        public void names(Set<String> out) {
        }

        @Override
        public String print() {
            return value == (long) value ? Long.toString((long) value) : Float.toString(value);
        }
    }

    record Input(String name, int id) implements Expr {
        @Override
        public float eval(float[] inputs) {
            return id < inputs.length ? inputs[id] : 0f;
        }

        @Override
        public void names(Set<String> out) {
            out.add(name);
        }

        @Override
        public String print() {
            return name;
        }
    }

    record Negate(Expr operand) implements Expr {
        @Override
        public float eval(float[] inputs) {
            return -operand.eval(inputs);
        }

        @Override
        public void names(Set<String> out) {
            operand.names(out);
        }

        @Override
        public String print() {
            return "-" + operand.print();
        }
    }

    record Binary(char op, Expr left, Expr right) implements Expr {
        @Override
        public float eval(float[] inputs) {
            return apply(op, left.eval(inputs), right.eval(inputs));
        }

        static float apply(char op, float a, float b) {
            float r = switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> b == 0f ? 0f : a / b;
                case '%' -> b == 0f ? 0f : a % b;
                default -> throw new IllegalStateException("operator " + op);
            };
            return Float.isFinite(r) ? r : 0f;
        }

        @Override
        public void names(Set<String> out) {
            left.names(out);
            right.names(out);
        }

        @Override
        public String print() {
            return "(" + left.print() + " " + op + " " + right.print() + ")";
        }
    }

    record Call(MathFunction function, List<Expr> args) implements Expr {
        @Override
        public float eval(float[] inputs) {
            return function.apply(args, inputs);
        }

        @Override
        public void names(Set<String> out) {
            args.forEach(arg -> arg.names(out));
        }

        @Override
        public String print() {
            return function.id() + "(" + args.stream().map(Expr::print).collect(Collectors.joining(", ")) + ")";
        }
    }
}
