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
        public SynchronizedStatement(String expression, int lineNumber) {
            super(lineNumber);
            this.expression = expression;
        }
        @Override
        public String toString() {
            return "Line " + lineNumber + ": synchronized(" + expression + ") { ... }";
        }
    }

    // Class representing a function/method declaration.
    public static class FunctionDeclaration {
        public String returnType;
        public String name;
        public List<Parameter> parameters;
        public int lineNumber; // the line where the function is declared
        public List<Statement> statements; // all statements inside the function body

        public FunctionDeclaration(String returnType, String name, List<Parameter> parameters, int lineNumber) {
            this.returnType = returnType;
            this.name = name;
            this.parameters = parameters;
            this.lineNumber = lineNumber;
            this.statements = new ArrayList<>();
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Line ").append(lineNumber).append(": ").append(returnType)
              .append(" ").append(name).append("(").append(parameters).append(")");
            sb.append("\n    Statements:");
            for (Statement stmt : statements) {
                sb.append("\n        ").append(stmt);
            }
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

    // Global collections (for declarations outside functions)
    private List<FunctionDeclaration> functions = new ArrayList<>();
    private List<Statement> globalStatements = new ArrayList<>();

    // Precompiled regex patterns as class-level constants
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "((?:public|protected|private|static|final|abstract|synchronized)\\s+)*" + // optional modifiers
            "([\\w<>\\[\\]]+)\\s+" +                   // return type
            "(\\w+)\\s*" +                            // method name
            "\\(([^)]*)\\)\\s*" +                      // parameter list
            "(?:throws\\s+[\\w\\s,]+)?\\s*\\{");        // optional throws clause and opening brace

    // Note: This pattern is simplistic and matches one variable declaration per line.
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "([\\w<>\\[\\]]+)\\s+" +    // variable type
            "(\\w+)\\s*" +              // variable name
            "(?:=\\s*[^;]+)?;");         // optional initialization

    private static final Pattern SYNCHRONIZED_PATTERN = Pattern.compile(
            "synchronized\\s*\\(([^)]+)\\)\\s*\\{");

    /**
     * Parses the given Java source file.
     * @param file The Java source file to parse.
     * @throws IOException if an I/O error occurs reading the file.
     */
    public void parse(File file) throws IOException {
        // Read all lines from the file.
        List<String> lines = Files.readAllLines(file.toPath());
        
        // We'll iterate line-by-line.
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Check for a function declaration.
            Matcher funcMatcher = FUNCTION_PATTERN.matcher(line);
            if (funcMatcher.find()) {
                String returnType = funcMatcher.group(2);
                String methodName = funcMatcher.group(3);
                String params = funcMatcher.group(4).trim();
                List<Parameter> paramList = new ArrayList<>();

                if (!params.isEmpty()) {
                    // Split parameters by comma and then by whitespace to separate type and name.
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
                // Create the function declaration and record the line number (1-indexed).
                FunctionDeclaration functionDecl = new FunctionDeclaration(returnType, methodName, paramList, i + 1);

                // Process the function body to capture its statements.
                // Start by counting the braces on the function declaration line.
                int braceCount = countOccurrences(line, '{') - countOccurrences(line, '}');
                // Continue reading lines until we match all opened braces.
                while (braceCount > 0 && i < lines.size() - 1) {
                    i++;
                    String bodyLine = lines.get(i);
                    // Parse the body line as a Statement.
                    Statement stmt = parseStatement(bodyLine, i + 1);
                    functionDecl.statements.add(stmt);
                    braceCount += countOccurrences(bodyLine, '{') - countOccurrences(bodyLine, '}');
                }
                functions.add(functionDecl);
                continue;
            }

            // Process global variable declarations and synchronized statements.
            Statement globalStmt = parseStatement(line, i + 1);
            // If the line was not empty (after trimming), record it globally.
            if (!(globalStmt instanceof GenericStatement && ((GenericStatement) globalStmt).text.isEmpty())) {
                globalStatements.add(globalStmt);
            }
        }
    }

    /**
     * Parses a single line as a Statement.
     * Checks if the line matches a variable declaration or a synchronized statement.
     * Otherwise, returns a GenericStatement containing the raw text.
     *
     * @param line The line of source code.
     * @param lineNumber The line number (1-indexed).
     * @return A Statement object representing the line.
     */
    private Statement parseStatement(String line, int lineNumber) {
        String trimmed = line.trim();
        // Check for variable declaration.
        Matcher varMatcher = VARIABLE_PATTERN.matcher(trimmed);
        if (varMatcher.find()) {
            String varType = varMatcher.group(1);
            String varName = varMatcher.group(2);
            return new VariableDeclaration(varType, varName, lineNumber);
        }
        // Check for synchronized statement.
        Matcher syncMatcher = SYNCHRONIZED_PATTERN.matcher(trimmed);
        if (syncMatcher.find()) {
            String expression = syncMatcher.group(1).trim();
            return new SynchronizedStatement(expression, lineNumber);
        }
        // Otherwise, return a generic statement.
        return new GenericStatement(trimmed, lineNumber);
    }

    // Helper method to count occurrences of a character in a string.
    private static int countOccurrences(String text, char c) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if (ch == c) {
                count++;
            }
        }
        return count;
    }

    // Methods to retrieve the parsed data.
    public List<FunctionDeclaration> getFunctions() {
        return functions;
    }

    public List<Statement> getGlobalStatements() {
        return globalStatements;
    }

    // Utility method to print parsed data.
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
