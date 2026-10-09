package com.eden.particles.impl.expr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExprParserTest {

    private static float eval(String text, Object... nameValues) {
        Expr expr = ExprParser.parse(text);
        float[] inputs = new float[InputNames.count()];
        for (int i = 0; i < nameValues.length; i += 2) {
            inputs[InputNames.id((String) nameValues[i])] = ((Number) nameValues[i + 1]).floatValue();
        }
        return expr.eval(inputs);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1 + 2 * 3            | 7",
            "(1 + 2) * 3          | 9",
            "10 - 4 - 3           | 3",
            "2 * -3               | -6",
            "--2                  | 2",
            "7 % 4                | 3",
            "1.5e1 / 3            | 5",
            ".5 + 0.25            | 0.75",
            "min(3, 1, 2)         | 1",
            "max(3, 1, 2)         | 3",
            "clamp(5, 0, 2)       | 2",
            "lerp(10, 20, 0.25)   | 12.5",
            "abs(-2) + floor(1.7) | 3",
            "ceil(1.2) + round(2.5) | 5",
            "sqrt(16) + pow(2, 3) | 12",
            "1 / 0                | 0",
            "sqrt(-4)             | 0",
            "pow(10, 100)         | 0",
    })
    void evaluatesWithPrecedenceAndSafeArithmetic(String text, float expected) {
        assertEquals(expected, eval(text), 1e-6);
    }

    @Test
    void constantPartsFoldAndInputsAreRead() {
        assertInstanceOf(Expr.Const.class, ExprParser.parse("max(2, 3) * (1 + 1)"));
        assertEquals(7f, eval("power * 2 + 1", "power", 3));
        assertEquals(0f, ExprParser.parse("power").eval(new float[0]), "missing inputs read as 0");
        Set<String> names = new TreeSet<>();
        ExprParser.parse("lerp(a, b, t) + a").names(names);
        assertEquals(Set.of("a", "b", "t"), names);
    }

    @Test
    void printedFormParsesBackToTheSameExpression() {
        Expr expr = ExprParser.parse("-power * (spread + 2) / max(1, count, 0.5)");
        assertEquals(expr, ExprParser.parse(expr.print()));
    }

    @Test
    void errorsNameTheColumnAndSuggestFunctions() {
        assertMessage("at column 5: expected a number, input or '('", "1 + ");
        assertMessage("at column 3: unexpected ')'", "1 ) 2");
        assertMessage("at column 1: unknown function 'lerpp' (did you mean 'lerp'?)", "lerpp(1, 2, 3)");
        assertMessage("clamp() takes 3 arguments, got 2", "clamp(1, 2)");
        assertMessage("expected ')'", "min(1, 2");
        assertMessage("empty expression", "  ");
        assertMessage("unexpected '#'", "#FF0000");
    }

    private static void assertMessage(String expected, String text) {
        String message = assertThrows(IllegalArgumentException.class, () -> ExprParser.parse(text)).getMessage();
        assertTrue(message.contains(expected), "expected '" + expected + "' in: " + message);
    }
}
