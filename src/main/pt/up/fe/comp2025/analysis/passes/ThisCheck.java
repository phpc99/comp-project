package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class ThisCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor()
    {
        addVisit(Kind.THIS,this::visitThis);
    }
    private Void visitThis(JmmNode node, SymbolTable table) {
        JmmNode methodNode = node.getAncestor(Kind.METHOD_DECL).orElse(null);
        if (methodNode == null) {
            return null;
        }
        boolean isStatic = methodNode.hasAttribute("hasStatic");
        if (isStatic) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "'this' Can't be used inside a static method.",
                    null)
            );
        }
        return null;
    }
}
