package dlisp.runtime;

import java.util.Map;
import java.util.StringJoiner;

public final class Printer {
    private Printer() {}

    public static String print(Value v) {
        return switch (v) {
            case Values.NilV __ -> "nil";
            case Values.BoolV b -> b.value() ? "true" : "false";
            case Values.IntV i -> Long.toString(i.value());
            case Values.DoubleV d -> {
                double x = d.value();
                if (Double.isNaN(x)) yield "NaN";
                if (Double.isInfinite(x)) yield x > 0 ? "Infinity" : "-Infinity";
                yield Double.toString(x);
            }
            case Values.StringV s -> "\""+ escape(s.value()) + "\"";
            case Values.SymbolV s -> s.name();
            case Values.KeywordV k -> ":" + k.name();
            case Values.ListV l -> "(" + join(l.items(), " ") + ")";
            case Values.VectorV vec -> "[" + join(vec.items(), " ") + "]";
            case Values.MapV m -> "{" + joinMap(m.items()) + "}";
            case Values.QuoteV q -> "'" + print(q.inner());
        };
    }

    private static String join(java.util.List<Value> items, String sep) {
        StringJoiner sj = new StringJoiner(sep);
        for (Value it : items) sj.add(print(it));
        return sj.toString();
    }

    private static String joinMap(Map<Value, Value> m) {
        StringJoiner sj = new StringJoiner(" ");
        for (var e : m.entrySet()) {
            sj.add(print(e.getKey()));
            sj.add(print(e.getValue()));
        }
        return sj.toString();
    }

    private static String escape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
    }
}
