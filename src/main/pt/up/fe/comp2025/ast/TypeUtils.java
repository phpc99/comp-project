package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;
import java.util.Optional;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {

    private final JmmSymbolTable table;
    private final String currentMethod;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
        this.currentMethod = null;
    }

    public TypeUtils(SymbolTable table, String currentMethod) {
        this.table = (JmmSymbolTable) table;
        this.currentMethod = currentMethod;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newVararg() { return new Type("...",true); }

    public static Type newIntArrayType() {
        return new Type("int", true);
    }

    public static Type newBoolType() {
        return new Type("boolean", false);
    }

    public static Type newStringType() { return new Type("string",false);}

    public static Type convertType(JmmNode typeNode) {
        var name = typeNode.get("name");
        var isArray = typeNode.get("isArray").equals("true");

        return new Type(name, isArray);
    }

    public Type getExprType(JmmNode expr) {

        Kind kind = Kind.fromString(expr.getKind());

        if (kind == Kind.BINARY_OP) {
            return switch (expr.get("op")){
                case  "*", "/", "+", "-" -> newIntType();
                case "<", ">", "&&" -> newBoolType();
                default -> null;
            };
        }
        if (kind == Kind.ARRAY_INDEX) return newIntType();
        if (kind == Kind.ARRAY_LENGTH) return newIntType();
        if (kind == Kind.METHOD_CALL) return table.getReturnType(expr.get("methodName"));
        if (kind == Kind.NEW_ARRAY) return newIntArrayType();
        if (kind == Kind.NEW_OBJECT) return new Type(expr.get("className"), false);
        if (kind == Kind.LOGICAL_NOT) return newBoolType();
        if (kind == Kind.PARENTHESES_OP) return getExprType(expr.getChild(0));
        if (kind == Kind.ARRAY_LITERAL) return newIntArrayType();
        if (kind == Kind.INTEGER) return newIntType();
        if (kind == Kind.TRUE) return newBoolType();
        if (kind == Kind.FALSE) return newBoolType();
        if (kind == Kind.IDENTIFIER) return getVarType(expr.get("var"));
        if (kind == Kind.THIS) return new Type(table.getClassName(), false);
        if (kind == Kind.INT_VARARG) return newVararg();

        return null;
    }

    public Type getVarType(String var) {
        List<Symbol> params = Optional.ofNullable(table.getParameters(currentMethod))
                .orElse(List.of());

        for (Symbol param : params) {
            if (param.getName().equals(var)) {
                System.out.println("Found param " + var + " in method " + currentMethod);
                return param.getType();
            }
        }

        List<Symbol> locals = Optional.ofNullable(table.getLocalVariables(currentMethod))
                .orElse(List.of());

        for (Symbol local : locals) {
            if (local.getName().equals(var)) {
                System.out.println("Found local " + var + " in method " + currentMethod);
                return local.getType();
            }
        }

        Type fieldType = getFieldType(var);
        if (fieldType != null) {
            System.out.println("Found field " + var);
        } else {
            System.out.println("Variable not found: " + var + " in method " + currentMethod);
        }

        return fieldType;
    }

    public Type getFieldType(String field) {
        List<Symbol> fields = Optional.ofNullable(table.getFields())
                .orElse(List.of());

        for (Symbol param : fields) {
            if (param.getName().equals(field)) {
                return param.getType();
            }
        }

        return null;
    }

    public static boolean isCompatibleType(Type varType, Type exprType, SymbolTable table) {
        if (varType.equals(exprType)) {
            return true;
        }

        return isSubtype(exprType.getName(), varType.getName(), table);
    }
    private static boolean isSubtype(String child, String parent, SymbolTable table) {
        if (child.equals(parent)) {
            return true;
        }

        String current = child;

        while (current != null && !current.isEmpty()) {
            String superClass = getSuperClass(current, table);
            if (superClass == null || superClass.isEmpty()) {
                return false;
            }
            if (superClass.equals(parent)) {
                return true;
            }
            current = superClass;
        }

        return false;
    }

    // Helper method to get the superclass of a given class
    private static String getSuperClass(String className, SymbolTable table) {
        if (table.getClassName().equals(className)) {
            return table.getSuper(); // Directly check the main class
        }

        // If more class hierarchy info were available, it could be stored in the table
        return null; // Assume no further hierarchy info is available
    }
}
