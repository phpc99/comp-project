package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;

public class MethodCheck extends AnalysisVisitor {
    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_CALL, this::visitMethods);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));
        return null;
    }
    private Void visitMethods(JmmNode node, SymbolTable table)
    {
        var methods = table.getMethods();
        var curr_method = node.get("methodName");
        String caller;


        if (node.getChild(0).getKind().equals("This")) {
            caller = table.getClassName();
        } else if (table.getImports().contains(node.getChild(0).get("var"))) {
            caller = node.getChild(0).get("var");
        } else {
            caller = typeUtils.getVarType(node.getChild(0).get("var")).getName();
        }
        var imports = table.getImports() != null ? table.getImports() : List.of();
        var extend = table.getSuper() != null ? table.getSuper() : "";
        var class_ = table.getClassName();
        if(methods.contains(curr_method) || imports.contains(caller)){
            return null;
        }
        if(class_.equals(caller) && imports.contains(extend))
        {
            return null;
        }
        addReport(Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                "Method '" + curr_method + "' doesn't exist in class " + class_,
                null)
        );
        return null;
    }
}
