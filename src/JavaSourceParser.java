import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

public class JavaSourceParser {

    // Abstract base class for all statements.
    public static abstract class Statement {
        public int lineNumber;
        public Statement(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    // A generic statement holding a raw line.
    public static class GenericStatement extends Statement {
        public String text;
        public GenericStatement(String text, int lineNumber) {
            super(lineNumber);
            this.text = text;
        }
        @Override
        public String toString() {
            return "Line " + lineNumber + ": " + text;
        }
    }

    // Statement type for variable declarations.
    public static class VariableDeclaration extends Statement {
        public String type;
        public String name;
        public VariableDeclaration(String type, String name, int lineNumber) {
            super(lineNumber);
            this.type = type;
            this.name = name;
        }
        @Override
        public String toString() {
            return "Line " + lineNumber + ": " + type + " " + name + ";";
        }
    }

    // Statement type for synchronized blocks.
    public static class SynchronizedStatement extends Statement {
        public String expression; // the monitor expression used in synchronized(...)
        public List<Statement> enclosedStatements;
        public SynchronizedStatement(String expression, int lineNumber) {
            super(lineNumber);
            this.expression = expression;
            this.enclosedStatements = new ArrayList<>();
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Line ").append(lineNumber)
              .append(": synchronized(").append(expression).append(") {");
            for (Statement stmt : enclosedStatements) {
                sb.append("\n    ").append(stmt.toString());
            }
            sb.append("\n}");
            return sb.toString();
        }
    }

    // Class representing a function/method declaration.
    public static class FunctionDeclaration {
        public String returnType;
        public String name;
        public List<Parameter> parameters;
        public int lineNumber; // the line where the function is declared
        public List<Statement> statements; // all statements inside the function body
        public boolean isSynchronized;   // true if declared with the synchronized modifier

        public FunctionDeclaration(String returnType, String name, List<Parameter> parameters, int lineNumber) {
            this.returnType = returnType;
            this.name = name;
            this.parameters = parameters;
            this.lineNumber = lineNumber;
            this.statements = new ArrayList<>();
            this.isSynchronized = false;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            // Note: The synchronized keyword is de-sugared away.
            sb.append("Line ").append(lineNumber)
              .append(": ").append(returnType)
              .append(" ").append(name)
              .append("(").append(parameters).append(") {");
            for (Statement stmt : statements) {
                sb.append("\n    ").append(stmt.toString());
            }
            sb.append("\n}");
            return sb.toString();
        }
    }

    // Class representing a function parameter.
    public static class Parameter {
        public String type;
        public String name;
        public Parameter(String type, String name) {
            this.type = type;
            this.name = name;
        }
        @Override
        public String toString() {
            return type + " " + name;
        }
    }

    // Global collections to hold parsed data.
    private List<FunctionDeclaration> functions = new ArrayList<>();
    private List<Statement> globalStatements = new ArrayList<>();

    // Precompiled regex patterns.
    // Function pattern includes modifiers (like synchronized) and the opening brace.
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "((?:public|protected|private|static|final|abstract|synchronized)\\s+)*" + // optional modifiers
            "([\\w<>\\[\\]]+)\\s+" +                   // return type
            "(\\w+)\\s*" +                            // method name
            "\\(([^)]*)\\)\\s*" +                      // parameter list
            "(?:throws\\s+[\\w\\s,]+)?\\s*\\{");        // optional throws clause and opening brace

    // Simplistic pattern for variable declarations (one per line).
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "([\\w<>\\[\\]]+)\\s+" +    // variable type
            "(\\w+)\\s*" +              // variable name
            "(?:=\\s*[^;]+)?;");         // optional initialization

    // Pattern for synchronized statements.
    private static final Pattern SYNCHRONIZED_PATTERN = Pattern.compile(
            "synchronized\\s*\\(([^)]+)\\)\\s*\\{");

    // Helper class to hold the result of parsing a block.
    private static class ParseResult {
        public List<Statement> statements;
        public int nextLineIndex; // index of the line with the closing brace
        public ParseResult(List<Statement> statements, int nextLineIndex) {
            this.statements = statements;
            this.nextLineIndex = nextLineIndex;
        }
    }

    /**
     * Recursively parses a block of code.
     * A block is expected to end with a line that contains only "}".
     * The closing brace is not added as a statement.
     *
     * @param lines The list of all source lines.
     * @param startIndex The index to start parsing from.
     * @return A ParseResult containing the list of statements in the block and the index of the closing brace.
     */
    private ParseResult parseBlock(List<String> lines, int startIndex) {
        List<Statement> statements = new ArrayList<>();
        int i = startIndex;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            // End of block.
            if (line.equals("}")) {
                return new ParseResult(statements, i);
            }
            // If the line starts a synchronized block, parse it recursively.
            Matcher syncMatcher = SYNCHRONIZED_PATTERN.matcher(line);
            if (syncMatcher.find()) {
                String expression = syncMatcher.group(1).trim();
                int syncLineNumber = i + 1;
                i++; // move past the synchronized header line
                ParseResult innerResult = parseBlock(lines, i);
                SynchronizedStatement syncStmt = new SynchronizedStatement(expression, syncLineNumber);
                syncStmt.enclosedStatements.addAll(innerResult.statements);
                statements.add(syncStmt);
                i = innerResult.nextLineIndex + 1;
                continue;
            }
            // Check for variable declaration.
            Matcher varMatcher = VARIABLE_PATTERN.matcher(line);
            if (varMatcher.find()) {
                String varType = varMatcher.group(1);
                String varName = varMatcher.group(2);
                statements.add(new VariableDeclaration(varType, varName, i + 1));
                i++;
                continue;
            }
            // Otherwise, treat the line as a generic statement.
            statements.add(new GenericStatement(line, i + 1));
            i++;
        }
        // If no closing brace is found, return what we have.
        return new ParseResult(statements, i);
    }

    /**
     * Parses the given Java source file.
     *
     * @param file The Java source file to parse.
     * @throws IOException if an I/O error occurs reading the file.
     */
    public void parse(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            // Skip empty lines.
            if (line.isEmpty()) {
                i++;
                continue;
            }
            // Check for a function declaration.
            Matcher funcMatcher = FUNCTION_PATTERN.matcher(line);
            if (funcMatcher.find()) {
                String modifiers = funcMatcher.group(1);
                String returnType = funcMatcher.group(2);
                String methodName = funcMatcher.group(3);
                String params = funcMatcher.group(4).trim();
                List<Parameter> paramList = new ArrayList<>();
                if (!params.isEmpty()) {
                    String[] paramParts = params.split(",");
                    for (String param : paramParts) {
                        param = param.trim();
                        String[] tokens = param.split("\\s+");
                        if (tokens.length >= 2) {
                            String paramType = tokens[0];
                            String paramName = tokens[1];
                            paramList.add(new Parameter(paramType, paramName));
                        }
                    }
                }
                FunctionDeclaration functionDecl = new FunctionDeclaration(returnType, methodName, paramList, i + 1);
                // Check if the function is declared as synchronized.
                if (modifiers != null && modifiers.contains("synchronized")) {
                    functionDecl.isSynchronized = true;
                }
                // The function header regex includes the opening brace; the body starts on the next line.
                i++;
                ParseResult result = parseBlock(lines, i);
                // If the function is synchronized, wrap its body inside a synchronized(this) block.
                if (functionDecl.isSynchronized) {
                    SynchronizedStatement outerSync = new SynchronizedStatement("this", functionDecl.lineNumber);
                    outerSync.enclosedStatements.addAll(result.statements);
                    functionDecl.statements.add(outerSync);
                } else {
                    functionDecl.statements.addAll(result.statements);
                }
                // Skip the closing brace line.
                i = result.nextLineIndex + 1;
                functions.add(functionDecl);
                continue;
            }
            // Process global-level synchronized block.
            Matcher syncMatcher = SYNCHRONIZED_PATTERN.matcher(line);
            if (syncMatcher.find()) {
                String expression = syncMatcher.group(1).trim();
                int syncLineNumber = i + 1;
                i++;
                ParseResult result = parseBlock(lines, i);
                SynchronizedStatement syncStmt = new SynchronizedStatement(expression, syncLineNumber);
                syncStmt.enclosedStatements.addAll(result.statements);
                globalStatements.add(syncStmt);
                i = result.nextLineIndex + 1;
                continue;
            }
            // Process global-level variable declaration.
            Matcher varMatcher = VARIABLE_PATTERN.matcher(line);
            if (varMatcher.find()) {
                String varType = varMatcher.group(1);
                String varName = varMatcher.group(2);
                globalStatements.add(new VariableDeclaration(varType, varName, i + 1));
                i++;
                continue;
            }
            // Otherwise, treat as a generic global statement (skip lone closing braces).
            if (line.equals("}")) {
                i++;
                continue;
            }
            globalStatements.add(new GenericStatement(line, i + 1));
            i++;
        }
    }

    // Methods to retrieve the parsed data.
    public List<FunctionDeclaration> getFunctions() {
        return functions;
    }

    public List<Statement> getGlobalStatements() {
        return globalStatements;
    }

    // Utility method to print the parsed data.
    public void printParsedData() {
        System.out.println("---- Function Declarations ----");
        for (FunctionDeclaration func : functions) {
            System.out.println(func);
        }
        System.out.println("\n---- Global Statements ----");
        for (Statement stmt : globalStatements) {
            System.out.println(stmt);
        }
    }

    // Main method to test the parser.
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java JavaSourceParser <source-file.java>");
            System.exit(1);
        }
        File file = new File(args[0]);
        JavaSourceParser parser = new JavaSourceParser();
        try {
            parser.parse(file);
            parser.printParsedData();
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
