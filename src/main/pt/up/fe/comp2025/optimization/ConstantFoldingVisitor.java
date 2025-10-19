package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.List;

/**
 * Visitor that performs constant folding optimization.
 * Evaluates constant expressions at compile time.
 */
public class ConstantFoldingVisitor extends PreorderJmmVisitor<Void, Boolean> {

    public ConstantFoldingVisitor() {
        buildVisitor();
    }

    @Override
    protected void buildVisitor() {
        addVisit(Kind.BINARY_OP, this::visitBinaryOp);
        addVisit(Kind.LOGICAL_NOT, this::visitLogicalNot);
        setDefaultVisit(this::defaultVisit);
    }

    /**
     * Visits binary operation nodes and folds them if both operands are constants.
     */
    private Boolean visitBinaryOp(JmmNode node, Void unused) {
        // First, visit children to fold any nested expressions
        boolean childrenChanged = false;
        for (JmmNode child : node.getChildren()) {
            Boolean childResult = visit(child);
            if (childResult != null && childResult) {
                childrenChanged = true;
            }
        }

        try {
            String op = node.get("op");
            JmmNode leftNode = node.getChild(0);
            JmmNode rightNode = node.getChild(1);

            Object leftValue = extractConstantValue(leftNode);
            Object rightValue = extractConstantValue(rightNode);

            // Check if both operands are constants
            if (leftValue != null && rightValue != null) {
                if (leftValue instanceof Integer && rightValue instanceof Integer) {
                    int left = (Integer) leftValue;
                    int right = (Integer) rightValue;

                    Integer result = performArithmeticOperation(op, left, right);

                    if (result != null) {
                        JmmNode constantNode = new JmmNodeImpl(List.of(Kind.INTEGER.getNodeName()));
                        constantNode.put("value", result.toString());

                        node.replace(constantNode);
                        return true;
                    }
                } else if (leftValue instanceof Boolean && rightValue instanceof Boolean) {
                    boolean left = (Boolean) leftValue;
                    boolean right = (Boolean) rightValue;

                    Boolean result = performLogicalOperation(op, left, right);

                    if (result != null) {
                        JmmNode constantNode = new JmmNodeImpl(List.of(result ?
                                Kind.TRUE.getNodeName() :
                                Kind.FALSE.getNodeName()));

                        node.replace(constantNode);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle any parsing errors
        }

        return childrenChanged;
    }

    /**
     * Visits logical NOT operations and folds them if the operand is a constant.
     */
    private Boolean visitLogicalNot(JmmNode node, Void unused) {
        // First, visit the child to fold any nested expressions
        boolean childChanged = false;
        for (JmmNode child : node.getChildren()) {
            Boolean childResult = visit(child);
            if (childResult != null && childResult) {
                childChanged = true;
            }
        }

        try {
            JmmNode operandNode = node.getChild(0);
            Object operandValue = extractConstantValue(operandNode);

            if (operandValue instanceof Boolean) {
                boolean value = (Boolean) operandValue;

                // Fold the NOT operation
                JmmNode constantNode = new JmmNodeImpl(List.of(!value ?
                        Kind.TRUE.getNodeName() :
                        Kind.FALSE.getNodeName()));

                node.replace(constantNode);
                return true;
            }
        } catch (Exception e) {
            // Silently handle any parsing errors
        }

        return childChanged;
    }

    /**
     * Extracts a constant value from a node if it is a constant.
     */
    private Object extractConstantValue(JmmNode node) {
        switch (Kind.fromString(node.getKind())) {
            case INTEGER:
                try {
                    return Integer.parseInt(node.get("value"));
                } catch (NumberFormatException e) {
                    return null;
                }
            case TRUE:
                return Boolean.TRUE;
            case FALSE:
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    /**
     * Performs arithmetic operations on constant integers.
     */
    private Integer performArithmeticOperation(String op, int left, int right) {
        switch (op) {
            case "+": return left + right;
            case "-": return left - right;
            case "*": return left * right;
            case "/": return right != 0 ? left / right : null; // Avoid division by zero
            case "<": return left < right ? 1 : 0;
            case ">": return left > right ? 1 : 0;
            default: return null;
        }
    }

    /**
     * Performs logical operations on constant booleans.
     */
    private Boolean performLogicalOperation(String op, boolean left, boolean right) {
        switch (op) {
            case "&&": return left && right;
            default: return null;
        }
    }

    /**
     * Default visit method for other node types.
     */
    private Boolean defaultVisit(JmmNode node, Void unused) {
        boolean changed = false;

        for (JmmNode child : node.getChildren()) {
            Boolean childResult = visit(child);
            if (childResult != null && childResult) {
                changed = true;
            }
        }

        return changed;
    }
}