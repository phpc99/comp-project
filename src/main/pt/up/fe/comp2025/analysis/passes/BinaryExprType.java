package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class BinaryExprType extends AnalysisVisitor {

    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_OP, this::visitBinaryOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));
        return null;
    }

    private Void visitBinaryOp(JmmNode jmmNode, SymbolTable table) {
        SpecsCheck.checkNotNull(typeUtils, () -> "Expected current method to be set");

        var op = jmmNode.get("op");
        var firstNode = jmmNode.getChild(0);
        var secondNode = jmmNode.getChild(1);
        System.out.println("FIRST NOOOOOOOOOOOOOOOOOODE: "+secondNode);
        var fisrtType = typeUtils.getExprType(firstNode);
        var secondType = typeUtils.getExprType(secondNode);
        System.out.println("SECOND TYYYYYYYYYYYYYYYYYYPE: "+secondType);

        if(fisrtType == null || secondType == null)
        {
            var message = String.format("Use of undeclared variable or imported method calls are not supported for binary operations");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    jmmNode.getLine(),
                    jmmNode.getColumn(),
                    message,
                    null)
            );
            return null;
        }
        var imp = table.getImports();
        Kind kind1 = Kind.fromString(firstNode.getKind());
        Kind kind2 = Kind.fromString(secondNode.getKind());
        if(kind1 == Kind.METHOD_CALL ) {
            Kind kindthis1 = Kind.fromString(firstNode.getChild(0).getKind());
            if (kindthis1 != Kind.THIS) {
                if (imp.contains(firstNode.getChild(0).get("var"))) {
                    var message = String.format("Use of imported method calls are not supported for binary operations");
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
        }
        if(kind2 == Kind.METHOD_CALL)
        {
            Kind kindthis2 = Kind.fromString(secondNode.getChild(0).getKind());
            if (kindthis2 != Kind.THIS) {
                if (imp.contains(secondNode.getChild(0).get("var"))) {
                    var message = String.format("Use of imported method calls are not supported for binary operations");
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
        }
        switch (op){
            case  "*", "/", "+", "-", "<", ">" -> {
                if (fisrtType.getName().equals("int") && secondType.getName().equals("int") &&
                    !fisrtType.isArray() && !secondType.isArray()) {
                    return null;
                }
            }
            case "&&" -> {
                if (fisrtType.getName().equals("boolean") && secondType.getName().equals("boolean") &&
                        !fisrtType.isArray() && !secondType.isArray()) {
                    return null;
                }
            }
        }

        var message = String.format("Error undeclared variable or use of superclass is not supported in binary operations");
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
