package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class ConditionType extends AnalysisVisitor {

    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.IF_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitIfStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));
        return null;
    }

    private Void visitIfStmt(JmmNode jmmNode, SymbolTable table) {
        SpecsCheck.checkNotNull(typeUtils, () -> "Expected current method to be set");

        var typeCondition = typeUtils.getExprType(jmmNode.getChild(0));

        if (typeCondition.getName().equals("boolean") && !typeCondition.isArray()) {
            return null;
        }

        var message = String.format("Error: Invalid operations inside the condition");
        addReport(Report.newError(
                Stage.SEMANTIC,
                jmmNode.getLine(),
                jmmNode.getColumn(),
                message,
                null)
        );

        return null;
    }

//    private Void visitWhileStmt(JmmNode jmmNode, SymbolTable table) {
//        SpecsCheck.checkNotNull(typeUtils, () -> "Expected current method to be set");
//
//        var type = typeUtils.getExprType(jmmNode.getChild(0));
//
//        if (type.getName().equals("boolean") && !type.isArray()) {
//            return null;
//        }
//
//        var message = String.format("Error");
//        addReport(Report.newError(
//                Stage.SEMANTIC,
//                jmmNode.getLine(),
//                jmmNode.getColumn(),
//                message,
//                null)
//        );
//
//        return null;
//    }
}
