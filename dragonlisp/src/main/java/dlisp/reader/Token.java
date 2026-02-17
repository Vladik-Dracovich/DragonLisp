package dlisp.reader;

public record Token(Type type, String text, int line, int col) {
    public enum Type {
        LPAREN, RPAREN,
        LBRACK, RBRACK,
        LBRACE, RBRACE,
        QUOTE,        // '
        STRING,
        NUMBER,
        SYMBOL,
        KEYWORD,      // :name
        EOF
    }
}
