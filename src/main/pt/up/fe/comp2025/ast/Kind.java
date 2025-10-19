package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.specs.util.SpecsStrings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Enum that mirrors the nodes that are supported by the AST.
 *
 * This enum allows to handle nodes in a safer and more flexible way that using strings with the names of the nodes.
 */
public enum Kind {
    PROGRAM,
    CLASS_DECL,
    VAR_DECL,
    TYPE,
    METHOD_DECL,
    PARAM,
    STMT,
    BLOCK_STMT,
    EXPR_STMT,
    ASSIGN_STMT,
    ARRAY_ASSIGN_STMT,
    RETURN_STMT,
    BINARY_EXPR,
    INTEGER_LITERAL,
    VAR_REF_EXPR,
    IMPORT_DECL,
    CLASS_NAME,
    SUPER_NAME,
    BINARY_OP,
    ARRAY_INDEX,
    ARRAY_LENGTH,
    METHOD_CALL,
    NEW_ARRAY,
    INT_VARARG,
    STRING,
    NEW_OBJECT,
    LOGICAL_NOT,
    PARENTHESES_OP,
    ARRAY_LITERAL,
    INTEGER,
    TRUE,
    FALSE,
    IDENTIFIER,
    THIS,
    IF_STMT,
    WHILE_STMT,
    IMPORT_NAME;


    private final String name;

    private Kind(String name) {
        this.name = name;
    }

    private Kind() {
        this.name = SpecsStrings.toCamelCase(name(), "_", true);
    }

    public static Kind fromString(String kind) {

        for (Kind k : Kind.values()) {
            if (k.getNodeName().equals(kind)) {
                return k;
            }
        }
        throw new RuntimeException("Could not convert string '" + kind + "' to a Kind");
    }

    public static List<String> toNodeName(Kind firstKind, Kind... otherKinds) {
        var nodeNames = new ArrayList<String>();
        nodeNames.add(firstKind.getNodeName());

        for(Kind kind : otherKinds) {
            nodeNames.add(kind.getNodeName());
        }

        return nodeNames;
    }

    public String getNodeName() {
        return name;
    }

    @Override
    public String toString() {
        return getNodeName();
    }

    /**
     * Tests if the given JmmNode has the same kind as this type.
     *
     * @param node
     * @return
     */
    public boolean check(JmmNode node) {
        return node.isInstance(this);
    }

    /**
     * Performs a check and throws if the test fails. Otherwise, does nothing.
     *
     * @param node
     */
    public void checkOrThrow(JmmNode node) {

        if (!check(node)) {
            throw new RuntimeException("Node '" + node + "' is not a '" + getNodeName() + "'");
        }
    }

    /**
     * Performs a check on all kinds to test and returns false if none matches. Otherwise, returns true.
     *
     * @param node
     * @param kindsToTest
     * @return
     */
    public static boolean check(JmmNode node, Kind... kindsToTest) {

        for (Kind k : kindsToTest) {

            // if any matches, return successfully
            if (k.check(node)) {

                return true;
            }
        }

        return false;
    }

    /**
     * Performs a check an all kinds to test and throws if none matches. Otherwise, does nothing.
     *
     * @param node
     * @param kindsToTest
     */
    public static void checkOrThrow(JmmNode node, Kind... kindsToTest) {
        if (!check(node, kindsToTest)) {
            // throw if none matches
            throw new RuntimeException("Node '" + node + "' is not any of " + Arrays.asList(kindsToTest));
        }
    }
}
