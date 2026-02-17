package dlisp.reader;

import dlisp.runtime.Value;
import dlisp.runtime.Values;

import java.util.*;

import static dlisp.reader.Token.Type.*;

public final class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens == null ? List.of(new Token(EOF, "", 1, 1)) : tokens;
    }

    public List<Value> parseAll() {
        List<Value> forms = new ArrayList<>();
        while (!check(EOF)) {
            forms.add(parseForm());
        }
        return forms;
    }

    public Value parseForm() {
        Token t = peek();
        return switch (t.type()) {
            case LPAREN -> parseList();
            case LBRACK -> parseVector();
            case LBRACE -> parseMap();
            case QUOTE -> { advance(); yield Values.quote(parseForm()); }
            case STRING -> { advance(); yield Values.s(t.text()); }
            case KEYWORD -> { advance(); yield Values.kw(t.text()); }
            case NUMBER -> { advance(); yield parseNumber(t); }
            case SYMBOL -> { advance(); yield parseSymbol(t); }
            case EOF -> throw error("Unexpected EOF", t);
            default -> throw error("Unexpected token: " + t.type(), t);
        };
    }

    private Value parseList() {
        Token open = expect(LPAREN);
        List<Value> items = new ArrayList<>();
        while (!check(RPAREN)) {
            if (check(EOF)) throw error("Unterminated list", open);
            items.add(parseForm());
        }
        expect(RPAREN);
        return Values.list(items);
    }

    private Value parseVector() {
        Token open = expect(LBRACK);
        List<Value> items = new ArrayList<>();
        while (!check(RBRACK)) {
            if (check(EOF)) throw error("Unterminated vector", open);
            items.add(parseForm());
        }
        expect(RBRACK);
        return Values.vec(items);
    }

    private Value parseMap() {
        Token open = expect(LBRACE);
        Map<Value, Value> m = new LinkedHashMap<>();
        while (!check(RBRACE)) {
            if (check(EOF)) throw error("Unterminated map", open);
            Value k = parseForm();
            if (check(RBRACE)) throw error("Map literal requires even number of forms", open);
            Value v = parseForm();
            m.put(k, v);
        }
        expect(RBRACE);
        return Values.map(m);
    }

    private Value parseNumber(Token t) {
        String s = t.text();
        try {
            if (s.contains(".")) return Values.d(Double.parseDouble(s));
            return Values.i(Long.parseLong(s));
        } catch (NumberFormatException e) {
            throw error("Bad number: " + s, t);
        }
    }

    private Value parseSymbol(Token t) {
        String s = t.text();
        return switch (s) {
            case "nil" -> Values.NIL;
            case "true" -> Values.bool(true);
            case "false" -> Values.bool(false);
            default -> Values.sym(s);
        };
    }

    // Helpers
    private boolean check(Token.Type type) { return peek().type() == type; }

    private Token peek() { return tokens.get(pos); }

    private Token advance() { return tokens.get(pos++); }

    private Token expect(Token.Type type) {
        Token t = peek();
        if (t.type() != type) throw error("Expected " + type + " but got " + t.type(), t);
        return advance();
    }

    private RuntimeException error(String msg, Token t) {
        return new RuntimeException("Reader error at " + t.line() + ":" + t.col() + " - " + msg);
    }
}
