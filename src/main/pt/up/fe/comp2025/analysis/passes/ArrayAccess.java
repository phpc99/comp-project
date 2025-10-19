package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class ArrayAccess extends AnalysisVisitor {

    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ARRAY_INDEX, this::visitBinaryOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));
        return null;
    }

    private Void visitBinaryOp(JmmNode jmmNode, SymbolTable table) {
        SpecsCheck.checkNotNull(typeUtils, () -> "Expected current method to be set");
        var ind = jmmNode.getChild(1);
        var typeArray = typeUtils.getExprType(jmmNode.getChild(0));
        var typeIndex = typeUtils.getExprType(ind);


        if(typeArray == null)
        {
            var message = String.format("Error");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    jmmNode.getLine(),
                    jmmNode.getColumn(),
                    message,
                    null)
            );
            return null;

        }
        if(typeIndex == null)
        {
            var imp = table.getImports();
            Kind kind1 = Kind.fromString(ind.getKind());
            if(kind1 == Kind.METHOD_CALL)
            {
                Kind kindthis1 = Kind.fromString(ind.getChild(0).getKind());
                if (kindthis1 != Kind.THIS) {
                    if (imp.contains(ind.getChild(0).get("var")) && typeArray.isArray()) {
                        return null;
                    }
                    var message = String.format("Error");
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            jmmNode.getLine(),
                            jmmNode.getColumn(),
                            message,
                            null)
                    );
                    return null;
                }
            }
            else{
                var message = String.format("Error");
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        jmmNode.getLine(),
                        jmmNode.getColumn(),
                        message,
                        null)
                );
                return null;
            }
        }

        if (typeArray.isArray() && typeIndex.getName().equals("int")) {
            return null;
        }

        var message = String.format("Error");
        addReport(Report.newError(
                Stage.SEMANTIC,
                jmmNode.getLine(),
                jmmNode.getColumn(),
                message,
                null)
        );

        return null;
    }
}