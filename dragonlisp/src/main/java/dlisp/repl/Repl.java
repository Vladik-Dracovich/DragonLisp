package dlisp.repl;

import dlisp.reader.Lexer;
import dlisp.reader.Parser;
import dlisp.runtime.Printer;
import dlisp.runtime.Value;

public final class Repl {
    public void run() {
        System.out.println("DragonLisp REPL (reader-only v0).");
        System.out.println("Type forms. Commands: :q / :quit");
        System.out.println();

        try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
            while (true) {
                try {
                    System.out.print("dl> ");
                    String line = br.readLine();
                    if (line == null) return;

                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.equals(":q") || line.equals(":quit")) return;

                    // Simple single-line reader for now (later: multi-line paren matching).
                    var tokens = new Lexer(line).lex();
                    var forms = new Parser(tokens).parseAll();
                    for (Value v : forms) {
                        System.out.println(Printer.print(v));
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
