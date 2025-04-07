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
        // Fields to record the type and declaration line of the monitor object.
        public String objectType;          // For a normal variable, its type; for "this", the current class name.
        public String objectDeclarationLine;  // Either a numeric line (as a string) or "ground".

        public SynchronizedStatement(String expression, int lineNumber) {
            super(lineNumber);
            this.expression = expression;
            this.enclosedStatements = new ArrayList<>();
            this.objectType = null;
            this.objectDeclarationLine = null;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Line ").append(lineNumber)
              .append(": synchronized(").append(expression);
            if (objectType != null) {
                sb.append(" /* type: ").append(objectType)
                  .append(", declared at: ").append(objectDeclarationLine)
                  .append(" */");
            }
            sb.append(") {");
            for (Statement stmt : enclosedStatements) {
                sb.append("\n    ").append(stmt.toString());
            }
            sb.append("\n}");
            return sb.toString();
        }
    }

    // Statement type for wait statements.
    public static class WaitStatement extends Statement {
        public String expression; // the variable name or "this"
        public String objectType; // the type of the variable or "this"
        public String objectDeclarationLine; // the line number where the variable is declared or "ground"
        public WaitStatement(String expression, int lineNumber) {
            super(lineNumber);
            this.expression = expression;
            this.objectType = null;
            this.objectDeclarationLine = null;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Line ").append(lineNumber)
              .append(": wait(").append(expression);
            if (objectType != null) {
                sb.append(" /* type: ").append(objectType)
                  .append(", declared at: ").append(objectDeclarationLine)
                  .append(" */");
            }
            sb.append(");");
            return sb.toString();
        }
    }

    // Class representing a function/method declaration.
    public static class FunctionDeclaration {
        public String className;
        public String returnType;
        public String name;
        public List<Parameter> parameters;
        public int lineNumber; // the line where the function is declared
        public List<Statement> statements; // all statements inside the function body
        public boolean isSynchronized;   // true if declared with the synchronized modifier

        public FunctionDeclaration(String returnType, String name, List<Parameter> parameters, int lineNumber) {
            this.className = null; // to be set later
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
    
    // Each node in the lock-dependancy graph is identified by a string (the lock’s unique ID).
    // An edge from node A to node B means that while holding lock A, lock B is acquired.
    public static class LockDependancyGraph {
        private Map<String, Set<String>> edges = new HashMap<>();
        
        public void addEdge(String from, String to) {
            if (edges.get(from) == null) {
                edges.put(from, new HashSet<>());
            }
            edges.get(from).add(to);
        }
        
        public Map<String, Set<String>> getEdges() {
            return edges;
        }

        
        public boolean hasCycle () {
            Set<String> visited = new HashSet<>();
            Set<String> onPath = new HashSet<>();
            List<String> path = new ArrayList<>();
            for (String node : edges.keySet()) {
                if (hasCycle(node, visited, onPath, path)) {
                    return true;
                }
            }
            return false;
        }

        private void printPath(List<String> path) {
            System.out.println("Potential deadlock: ");
            StringBuilder pathStr = new StringBuilder();
            for (String node : path) {
                pathStr.append(node);
                pathStr.append("->");
            }
            pathStr.append(path.get(0));
            System.out.println(pathStr.toString());
        }

        private boolean hasCycle (String node, Set<String> visited, Set<String> onPath, List<String> path) {
            if (onPath.contains(node)) {
                printPath(path);
                return true;
            }
            if (visited.contains(node)) {
                return false;
            }
            onPath.add(node);
            path.addLast(node);
            for (String neighbor : edges.getOrDefault(node, new HashSet<>())) {
                if (hasCycle(neighbor, visited, onPath, path)) {
                    return true;
                }
            }
            path.removeLast();
            onPath.remove(node);
            return false;
        }

        public List<List<String>> detectAllCycles() {
            List<List<String>> cycles = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Set<String> onPath = new HashSet<>();
            List<String> path = new ArrayList<>();
            for (String node : edges.keySet()) {
                detectAllCycles(node, visited, onPath, path, cycles);
            }
            return cycles;
        }

        private void detectAllCycles (String node, Set<String> visited, Set<String> onPath, List<String> path, List<List<String>> cycles) {
            if (onPath.contains(node)) {
                List<String> addPath = new ArrayList<> (path);
                addPath.add(node);
                cycles.add(addPath);
                return;
            }
            if (visited.contains(node)) {
                return;
            }
            onPath.add(node);
            path.addLast(node);
            for (String neighbor : edges.getOrDefault(node, new HashSet<>())) {
                detectAllCycles(neighbor, visited, onPath, path, cycles);
            }
            path.removeLast();
            onPath.remove(node);
            
        }
        
        public void printGraph() {
            System.out.println("Lock Order Graph:");
            for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
                String from = entry.getKey();
                for (String to : entry.getValue()) {
                    System.out.println("  " + from + " -> " + to);
                }
            }
        }
    }
    
    // Global collections to hold parsed data.
    private final List<FunctionDeclaration> functions = new ArrayList<>();
    private final List<Statement> globalStatements = new ArrayList<>();
    // Global symbol table for top-level declarations.
    private final Map<String, VariableDeclaration> globalSymbols = new HashMap<>();
    
    // Current class name found in the source file.
    private String currentClass = "Unknown";
    
    // Precompiled regex patterns.
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "((?:public|protected|private|static|final|abstract|synchronized)\\s+)*" + // optional modifiers
            "([\\w<>\\[\\]]+)\\s+" +                   // return type
            "(\\w+)\\s*" +                            // method name
            "\\(([^)]*)\\)\\s*" +                      // parameter list
            "(?:throws\\s+[\\w\\s,]+)?\\s*\\{");        // optional throws clause and opening brace

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "([\\w<>\\[\\]]+)\\s+" +    // variable type
            "(\\w+)\\s*" +              // variable name
            "(?:=\\s*[^;]+)?;");         // optional initialization

    private static final Pattern SYNCHRONIZED_PATTERN = Pattern.compile(
            "synchronized\\s*\\(([^)]+)\\)\\s*\\{");

    private static final Pattern WAI_PATTERN = Pattern.compile(
            "(?:(\\w+)\\s*\\.)?" +         // optional variable name
            "wait\\s*\\(\\s*\\)\\s*;");
    private static final Pattern SIMPLE_IDENTIFIER_PATTERN = Pattern.compile("^\\w+$");

    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+(\\w+)");

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
     * @param localSymbols The symbol table (variable name -> VariableDeclaration) for the current block.
     * @return A ParseResult containing the list of statements in the block and the index of the closing brace.
     */
    private ParseResult parseBlock(List<String> lines, int startIndex, Map<String, VariableDeclaration> localSymbols) {
        List<Statement> statements = new ArrayList<>();
        int i = startIndex;
        // Inherit the parent symbol table.
        Map<String, VariableDeclaration> currentSymbols = new HashMap<>(localSymbols);
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            if (line.equals("}")) {
                return new ParseResult(statements, i);
            }
            // Handle synchronized blocks.
            Matcher syncMatcher = SYNCHRONIZED_PATTERN.matcher(line);
            if (syncMatcher.find()) {
                String expression = syncMatcher.group(1).trim();
                int syncLineNumber = i + 1;
                i++; // move past synchronized header
                ParseResult innerResult = parseBlock(lines, i, currentSymbols);
                SynchronizedStatement syncStmt = new SynchronizedStatement(expression, syncLineNumber);
                if ("this".equals(expression)) {
                    syncStmt.objectType = currentClass;
                    syncStmt.objectDeclarationLine = "ground";
                } else {
                    Matcher idMatcher = SIMPLE_IDENTIFIER_PATTERN.matcher(expression);
                    if (idMatcher.find()) {
                        VariableDeclaration decl = currentSymbols.get(expression);
                        if (decl != null) {
                            syncStmt.objectType = decl.type;
                            syncStmt.objectDeclarationLine = String.valueOf(decl.lineNumber);
                        }
                    }
                }
                syncStmt.enclosedStatements.addAll(innerResult.statements);
                statements.add(syncStmt);
                i = innerResult.nextLineIndex + 1;
                continue;
            }
            // Handle wait statements.
            Matcher waitMatcher = WAI_PATTERN.matcher(line);
            if (waitMatcher.find()) {
                String expression = waitMatcher.group(1);
                if (expression == null) {
                    expression = "this";
                }
                int waitLineNumber = i + 1;
                WaitStatement waitStmt = new WaitStatement(expression, waitLineNumber);
                if ("this".equals(expression)) {
                    waitStmt.objectType = currentClass;
                    waitStmt.objectDeclarationLine = "ground";
                } else {
                    Matcher idMatcher = SIMPLE_IDENTIFIER_PATTERN.matcher(expression);
                    if (idMatcher.find()) {
                        VariableDeclaration decl = currentSymbols.get(expression);
                        if (decl != null) {
                            waitStmt.objectType = decl.type;
                            waitStmt.objectDeclarationLine = String.valueOf(decl.lineNumber);
                        }
                    }
                }
                statements.add(waitStmt);
                i++;
                continue;
            }

            // Variable declarations.
            Matcher varMatcher = VARIABLE_PATTERN.matcher(line);
            if (varMatcher.find()) {
                String varType = varMatcher.group(1);
                String varName = varMatcher.group(2);
                VariableDeclaration varDecl = new VariableDeclaration(varType, varName, i + 1);
                statements.add(varDecl);
                currentSymbols.put(varName, varDecl);
                i++;
                continue;
            }
            // Otherwise, a generic statement.
            statements.add(new GenericStatement(line, i + 1));
            i++;
        }
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
        // Extract current class name.
        for (String l : lines) {
            Matcher classMatcher = CLASS_PATTERN.matcher(l);
            if (classMatcher.find()) {
                currentClass = classMatcher.group(1);
                break;
            }
        }
        
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                i++;
                continue;
            }
            // Function declarations.
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
                            paramList.add(new Parameter(tokens[0], tokens[1]));
                        }
                    }
                }
                FunctionDeclaration functionDecl = new FunctionDeclaration(returnType, methodName, paramList, i + 1);
                functionDecl.className = currentClass;
                if (modifiers != null && modifiers.contains("synchronized")) {
                    functionDecl.isSynchronized = true;
                }
                // Build symbol table with parameters.
                Map<String, VariableDeclaration> localSymbols = new HashMap<>();
                for (Parameter p : paramList) {
                    localSymbols.put(p.name, new VariableDeclaration(p.type, p.name, functionDecl.lineNumber));
                }
                i++; // move past function header
                ParseResult result = parseBlock(lines, i, localSymbols);
                if (functionDecl.isSynchronized) {
                    SynchronizedStatement outerSync = new SynchronizedStatement("this", functionDecl.lineNumber);
                    outerSync.enclosedStatements.addAll(result.statements);
                    outerSync.objectType = currentClass;
                    outerSync.objectDeclarationLine = "ground";
                    functionDecl.statements.add(outerSync);
                } else {
                    functionDecl.statements.addAll(result.statements);
                }
                i = result.nextLineIndex + 1;
                functions.add(functionDecl);
                continue;
            }
            // Global-level synchronized blocks.
            Matcher syncMatcher = SYNCHRONIZED_PATTERN.matcher(line);
            if (syncMatcher.find()) {
                String expression = syncMatcher.group(1).trim();
                int syncLineNumber = i + 1;
                i++;
                ParseResult result = parseBlock(lines, i, globalSymbols);
                SynchronizedStatement syncStmt = new SynchronizedStatement(expression, syncLineNumber);
                if ("this".equals(expression)) {
                    syncStmt.objectType = currentClass;
                    syncStmt.objectDeclarationLine = "ground";
                } else {
                    Matcher idMatcher = SIMPLE_IDENTIFIER_PATTERN.matcher(expression);
                    if (idMatcher.find()) {
                        VariableDeclaration decl = globalSymbols.get(expression);
                        if (decl != null) {
                            syncStmt.objectType = decl.type;
                            syncStmt.objectDeclarationLine = String.valueOf(decl.lineNumber);
                        }
                    }
                }
                syncStmt.enclosedStatements.addAll(result.statements);
                globalStatements.add(syncStmt);
                i = result.nextLineIndex + 1;
                continue;
            }
            // Global-level variable declarations.
            Matcher varMatcher = VARIABLE_PATTERN.matcher(line);
            if (varMatcher.find()) {
                String varType = varMatcher.group(1);
                String varName = varMatcher.group(2);
                VariableDeclaration varDecl = new VariableDeclaration(varType, varName, i + 1);
                globalStatements.add(varDecl);
                globalSymbols.put(varName, varDecl);
                i++;
                continue;
            }
            if (line.equals("}")) {
                i++;
                continue;
            }
            globalStatements.add(new GenericStatement(line, i + 1));
            i++;
        }
    }
    
    /**
     * Produces a unique lock identifier for a synchronized statement.
     * The lock ID is marked with the class name and the line number where the monitor was declared.
     * For "this", returns currentClass + ":ground". Otherwise, returns objectType + ":" + objectDeclarationLine.
     */
    private String getLockId(String functionClass, SynchronizedStatement sync) {
        if ("this".equals(sync.expression)) {
            return functionClass + ":ground";
        } else if (sync.objectType != null && sync.objectDeclarationLine != null) {
            return sync.objectType + ":" + sync.objectDeclarationLine;
        } else {
            return sync.expression;
        }
    }

    private String getLockId(String functionClass, WaitStatement sync) {
        if ("this".equals(sync.expression)) {
            return functionClass + ":ground";
        } else if (sync.objectType != null && sync.objectDeclarationLine != null) {
            return sync.objectType + ":" + sync.objectDeclarationLine;
        } else {
            return sync.expression;
        }
    }
    
    /**
     * Recursively traverses a list of statements and records nested lock acquisitions.
     * When a synchronized statement is encountered, an edge is added from most-recent lock
     * to the new lock.
     */
    private void traverseStatements(String functionClass, List<Statement> statements, Deque<String> lockStack, LockDependancyGraph graph) {
        for (Statement stmt : statements) {
            if (stmt instanceof SynchronizedStatement) {
                SynchronizedStatement sync = (SynchronizedStatement) stmt;
                String lockId = getLockId(functionClass, sync);
                if (!lockStack.isEmpty()) {
                    graph.addEdge(lockStack.getFirst(), lockId);
                }
                lockStack.push(lockId);
                traverseStatements(functionClass, sync.enclosedStatements, lockStack, graph);
                lockStack.pop();
            }
            else if (stmt instanceof WaitStatement) {
                WaitStatement waitStmt = (WaitStatement) stmt;
                String lockId = getLockId(functionClass, waitStmt);
                // wait statements are represented as edges from the current lock to the wait lock.
                // if the wait lock is also the most recent lock, we don't add an edge.
                if (!lockStack.isEmpty() && !lockStack.getFirst().equals(lockId)) {
                    graph.addEdge(lockStack.getFirst(), lockId);
                }
                // wait statements are not added to the lock stack.
            }
        }
    }
    
    /**
     * Builds and returns a lock-dependancy graph for a given function.
     * This graph represents the nested lock acquisitions within that function.
     */
    public LockDependancyGraph buildLockDependancyGraphForFunction(FunctionDeclaration function) {
        LockDependancyGraph graph = new LockDependancyGraph();
        Deque<String> lockStack = new ArrayDeque<>();
        traverseStatements(function.className, function.statements, lockStack, graph);
        return graph;
    }
    

    private String getCanonicalNodeId(String id) {
        String [] splitId = id.split(":");
        return splitId[0];
    }

    /**
     * Merge the lock-dependancy graphs from all functions
     * into one global lock-dependancy graph.
     */
    public LockDependancyGraph mergeGlobalLockDependancyGraph() {
        LockDependancyGraph mergedGraph = new LockDependancyGraph();
        // Aggregate edges from each function.
        for (FunctionDeclaration func : functions) {
            LockDependancyGraph localGraph = buildLockDependancyGraphForFunction(func);
            for (Map.Entry<String, Set<String>> entry : localGraph.getEdges().entrySet()) {
                String from = getCanonicalNodeId(entry.getKey());
                for (String to : entry.getValue()) {
                    to = getCanonicalNodeId(to);
                    mergedGraph.addEdge(from, to);
                }
            }
        }
        return mergedGraph;
    }
    
    public List<FunctionDeclaration> getFunctions() {
        return functions;
    }

    public List<Statement> getGlobalStatements() {
        return globalStatements;
    }

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
    // Updated to support processing multiple files
    public void parseAllFiles(List<File> files) throws IOException {
        // Clear any existing state
        functions.clear();
        globalStatements.clear();
        globalSymbols.clear();
        
        // Parse each file
        for (File file : files) {
            parse(file);
        }
    }

    // Updated main method to handle multiple files
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java JavaSourceParser <source-file1.java> [source-file2.java ...]");
            System.exit(1);
        }

        List<File> files = new ArrayList<>();
        for (String arg : args) {
            files.add(new File(arg));
        }

        JavaSourceParser parser = new JavaSourceParser();
        try {
            parser.parseAllFiles(files);
            parser.printParsedData();
            
            System.out.println("\n---- Lock-dependancy graphs (Local per Function) ----");
            for (FunctionDeclaration func : parser.getFunctions()) {
                System.out.println("Function " + func.name + ":");
                LockDependancyGraph localGraph = parser.buildLockDependancyGraphForFunction(func);
                localGraph.printGraph();
                System.out.println();
            }

            System.out.println("---- Merged global lock-dependancy graph ----");
            LockDependancyGraph mergedGraph = parser.mergeGlobalLockDependancyGraph();
            mergedGraph.printGraph();
            
            List<List<String>> cycles = mergedGraph.detectAllCycles();
            System.out.println("Potential deadlock paths: " + cycles.toString());
        } catch (IOException e) {
            System.err.println("Error reading files: " + e.getMessage());
            e.printStackTrace();
        }
    }
}