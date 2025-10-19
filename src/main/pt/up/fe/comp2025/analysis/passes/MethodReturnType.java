package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class MethodReturnType extends AnalysisVisitor {

    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));

        // Case when return type is void and no return statement
        if (table.getReturnType(method.get("methodName")).getName().equals("void")
                && !method.hasAttribute("hasReturn")) {
            return null;
        }

        var returnType = table.getReturnType(method.get("methodName"));
        var imports = table.getImports();

        if (method.getChildren().isEmpty()) {
            return null;
        }

        var lastChild = method.getChildren().getLast();
        var exprType = typeUtils.getExprType(lastChild);

        if (exprType == null || imports.contains(exprType.getName())) {
            return null;
        }

        if (returnType.getName().equals(exprType.getName())) {
            return null;
        }
        addReport(Report.newError(
                Stage.SEMANTIC,
                method.getLine(),
                method.getColumn(),
                "Return type mismatch in method",
                null)
        );
        return null;
    }
}