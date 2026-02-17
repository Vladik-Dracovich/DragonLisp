package dlisp;

import dlisp.reader.Lexer;
import dlisp.reader.Parser;
import dlisp.runtime.Printer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReaderTests {

    @Test
    void parsesListsVectorsMapsAndQuote() {
        var src = "(+ 1 2) [a :b \"c\"] {'x 10 :k 20}";
        var forms = new Parser(new Lexer(src).lex()).parseAll();

        assertEquals(3, forms.size());
        assertEquals("(+ 1 2)", Printer.print(forms.get(0)));
        assertEquals("[a :b \"c\"]", Printer.print(forms.get(1)));
        assertEquals("{'x 10 :k 20}", Printer.print(forms.get(2)));
    }

    @Test
    void parsesBooleansAndNil() {
        var src = "(nil true false)";
        var forms = new Parser(new Lexer(src).lex()).parseAll();
        assertEquals("(nil true false)", Printer.print(forms.get(0)));
    }
}
