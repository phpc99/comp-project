package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;

public class ArgumentsCheck extends AnalysisVisitor {
    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_CALL, this::visitArguments);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));
        return null;
    }

    private Void visitArguments(JmmNode node, SymbolTable table) {
        var methodName = node.get("methodName");
        var expectedParams = table.getParameters(methodName);

        if (expectedParams == null) {
            System.out.println("Couldn't get this method's parameters, it was probably imported, so we are assuming it's correct.");
            return null;
        }

        var arguments = node.getChildren().subList(1, node.getNumChildren()); // The first child is always the caller.

        System.out.println("Method: " + methodName);
        System.out.println("Expected parameters: " + expectedParams);
        System.out.println("Provided arguments: " + arguments);

        if (expectedParams.isEmpty()) {
            if (!arguments.isEmpty()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Method '" + methodName + "' does not take arguments, but got " + arguments.size(),
                        null)
                );
            }
            return null;
        }

        int expectedCount = expectedParams.size();
        Type lastParamType = expectedParams.get(expectedCount - 1).getType();
        boolean hasVarargs = lastParamType.isArray() && lastParamType.getName().equals("...");  // Check for vararg

        System.out.println("Vararg Test: " + expectedParams.get(expectedCount - 1));
        int fixedParams = hasVarargs ? expectedCount - 1 : expectedCount;

        if (arguments.size() < fixedParams) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Method '" + methodName + "' expects at least " + fixedParams + " arguments but got " + arguments.size(),
                    null)
            );
            return null;
        }

        for (int i = 0; i < fixedParams; i++) {
            Type expectedType = expectedParams.get(i).getType();
            Type actualType = typeUtils.getExprType(arguments.get(i));
            if(actualType == null)
            {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Type mismatch in method '",
                        null)
                );
            }
            if (!TypeUtils.isCompatibleType(expectedType, actualType, table)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Type mismatch in method '" + methodName + "': expected argument " +
                                (i + 1) + " to be " + expectedType.getName() +
                                " but got " + actualType.getName(),
                        null)
                );
            }
        }

        // Check varargs (all remaining arguments should be of type int)
        if (hasVarargs) {
            Type varargType = new Type("int", false); // vararg is only for ints

            for (int i = fixedParams; i < arguments.size(); i++) {
                Type actualType = typeUtils.getExprType(arguments.get(i));
                if(actualType == null)
                {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            node.getLine(),
                            node.getColumn(),
                            "Type mismatch in method '",
                            null)
                    );
                }

                if (!TypeUtils.isCompatibleType(varargType, actualType, table)) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            node.getLine(),
                            node.getColumn(),
                            "Type mismatch in method '" + methodName + "': varargs argument " +
                                    (i + 1) + " should be of type int but got " + actualType.getName(),
                            null)
                    );
                }
            }
        }
        return null;
    }
}

