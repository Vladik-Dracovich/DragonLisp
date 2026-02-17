package dlisp.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Values {
    private Values() {}

    public static final NilV NIL = new NilV();

    public record NilV() implements Value {}
    public record BoolV(boolean value) implements Value {}
    public record IntV(long value) implements Value {}
    public record DoubleV(double value) implements Value {}
    public record StringV(String value) implements Value {
        public StringV {
            Objects.requireNonNull(value);
        }
    }
    public record SymbolV(String name) implements Value {
        public SymbolV {
            Objects.requireNonNull(name);
        }
    }
    public record KeywordV(String name) implements Value {
        public KeywordV {
            Objects.requireNonNull(name);
        }
    }

    public record ListV(List<Value> items) implements Value {
        public ListV {
            Objects.requireNonNull(items);
        }
    }

    public record VectorV(List<Value> items) implements Value {
        public VectorV {
            Objects.requireNonNull(items);
        }
    }

    public record MapV(Map<Value, Value> items) implements Value {
        public MapV {
            Objects.requireNonNull(items);
        }
    }

    /** Reader-level quote wrapper: 'x becomes (quote x) later or kept as QuoteV for macroexpansion. */
    public record QuoteV(Value inner) implements Value {
        public QuoteV {
            Objects.requireNonNull(inner);
        }
    }

    public static BoolV bool(boolean b) { return new BoolV(b); }
    public static IntV i(long v) { return new IntV(v); }
    public static DoubleV d(double v) { return new DoubleV(v); }
    public static StringV s(String v) { return new StringV(v); }
    public static SymbolV sym(String v) { return new SymbolV(v); }
    public static KeywordV kw(String v) { return new KeywordV(v); }
    public static ListV list(List<Value> v) { return new ListV(v); }
    public static VectorV vec(List<Value> v) { return new VectorV(v); }
    public static MapV map(Map<Value, Value> v) { return new MapV(v); }
    public static QuoteV quote(Value v) { return new QuoteV(v); }
}
