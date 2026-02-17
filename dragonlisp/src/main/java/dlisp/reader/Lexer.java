package dlisp.reader;

import java.util.ArrayList;
import java.util.List;

import static dlisp.reader.Token.Type.*;

public final class Lexer {
    private final String src;
    private int i = 0;
    private int line = 1;
    private int col = 1;

    public Lexer(String src) {
        this.src = src == null ? "" : src;
    }

    public List<Token> lex() {
        List<Token> out = new ArrayList<>();
        while (true) {
            skipWhitespaceAndComments();
            if (eof()) {
                out.add(new Token(EOF, "", line, col));
                return out;
            }
            char c = peek();
            int startLine = line;
            int startCol = col;

            switch (c) {
                case '(' -> { advance(); out.add(new Token(LPAREN, "(", startLine, startCol)); }
                case ')' -> { advance(); out.add(new Token(RPAREN, ")", startLine, startCol)); }
                case '[' -> { advance(); out.add(new Token(LBRACK, "[", startLine, startCol)); }
                case ']' -> { advance(); out.add(new Token(RBRACK, "]", startLine, startCol)); }
                case '{' -> { advance(); out.add(new Token(LBRACE, "{", startLine, startCol)); }
                case '}' -> { advance(); out.add(new Token(RBRACE, "}", startLine, startCol)); }
                case '\'' -> { advance(); out.add(new Token(QUOTE, "'", startLine, startCol)); }
                case '"' -> out.add(readString(startLine, startCol));
                case ':' -> out.add(readKeyword(startLine, startCol));
                default -> {
                    if (isNumberStart(c)) out.add(readNumber(startLine, startCol));
                    else out.add(readSymbol(startLine, startCol));
                }
            }
        }
    }

    private void skipWhitespaceAndComments() {
        while (!eof()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
                continue;
            }
            if (c == ';') { // comment until end of line
                while (!eof() && peek() != '\n') advance();
                continue;
            }
            break;
        }
    }

    private Token readString(int startLine, int startCol) {
        // consume opening "
        advance();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (eof()) throw error("Unterminated string", startLine, startCol);
            char c = advance();
            if (c == '"') break;
            if (c == '\\') {
                if (eof()) throw error("Unterminated string escape", startLine, startCol);
                char e = advance();
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> throw error("Unknown escape: \\" + e, startLine, startCol);
                }
            } else {
                sb.append(c);
            }
        }
        return new Token(STRING, sb.toString(), startLine, startCol);
    }

    private Token readKeyword(int startLine, int startCol) {
        // consume ':'
        advance();
        StringBuilder sb = new StringBuilder();
        while (!eof() && !isDelimiter(peek())) {
            sb.append(advance());
        }
        if (sb.isEmpty()) throw error("Empty keyword", startLine, startCol);
        return new Token(KEYWORD, sb.toString(), startLine, startCol);
    }

    private Token readNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        if (peek() == '-') sb.append(advance());
        boolean sawDot = false;
        while (!eof()) {
            char c = peek();
            if (Character.isDigit(c)) {
                sb.append(advance());
            } else if (c == '.' && !sawDot) {
                sawDot = true;
                sb.append(advance());
            } else {
                break;
            }
        }
        return new Token(NUMBER, sb.toString(), startLine, startCol);
    }

    private Token readSymbol(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (!eof() && !isDelimiter(peek())) {
            sb.append(advance());
        }
        String text = sb.toString();
        if (text.isEmpty()) throw error("Unexpected character: " + peek(), startLine, startCol);
        return new Token(SYMBOL, text, startLine, startCol);
    }

    private boolean isDelimiter(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' || c == '[' || c == ']' ||
               c == '{' || c == '}' || c == '"' || c == '\'' || c == ';';
    }

    private boolean isNumberStart(char c) {
        if (Character.isDigit(c)) return true;
        if (c == '-') {
            // '-' is number start only if next is digit
            return !eof(1) && Character.isDigit(peek(1));
        }
        return false;
    }

    private boolean eof() { return i >= src.length(); }
    private boolean eof(int ahead) { return i + ahead >= src.length(); }

    private char peek() { return src.charAt(i); }
    private char peek(int ahead) { return src.charAt(i + ahead); }

    private char advance() {
        char c = src.charAt(i++);
        if (c == '\n') { line++; col = 1; }
        else col++;
        return c;
    }

    private RuntimeException error(String msg, int atLine, int atCol) {
        return new RuntimeException("Reader error at " + atLine + ":" + atCol + " - " + msg);
    }
}
