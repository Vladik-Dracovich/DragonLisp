package dlisp.runtime;

public sealed interface Value permits
        Values.NilV,
        Values.BoolV,
        Values.IntV,
        Values.DoubleV,
        Values.StringV,
        Values.SymbolV,
        Values.KeywordV,
        Values.ListV,
        Values.VectorV,
        Values.MapV,
        Values.QuoteV {

    default String typeName() {
        return switch (this) {
            case Values.NilV __ -> "nil";
            case Values.BoolV __ -> "bool";
            case Values.IntV __ -> "int";
            case Values.DoubleV __ -> "double";
            case Values.StringV __ -> "string";
            case Values.SymbolV __ -> "symbol";
            case Values.KeywordV __ -> "keyword";
            case Values.ListV __ -> "list";
            case Values.VectorV __ -> "vector";
            case Values.MapV __ -> "map";
            case Values.QuoteV __ -> "quote";
        };
    }
}
