package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.Objects;

public class ArrayCheckType extends AnalysisVisitor {
    private TypeUtils typeUtils;

    @Override
    public void buildVisitor()
    {
        addVisit(Kind.ARRAY_LITERAL,this::visitArray);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ARRAY_ASSIGN_STMT, this::visitArrayAssign);
    }
    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));
        return null;
    }
    private Void visitArray(JmmNode node, SymbolTable table) {
        System.out.println("ENTRASTE NO VISITARRAY");
        var expr = typeUtils.getExprType(node.getChild(0));
        for(JmmNode child: node.getChildren())
        {
            var expr_ = typeUtils.getExprType(child);
            if(!expr_.equals(expr))
            {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Type mismatch: different types of variables are not allowed in the same array",
                        null)
                );
                return null;
            }
        }
        return null;
    }
    private Void visitArrayAssign(JmmNode node, SymbolTable table) {
        var tipo = typeUtils.getExprType(node.getChild(0));
        if(tipo != null)
        {

            if(!Objects.equals(tipo.getName(), "int"))
            {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Index is not an integer",
                        null)
                );
            }
            return null;
        }
        else {
            var imp = table.getImports();
            Kind kind1 = Kind.fromString(node.getChild(0).getKind());
            if(kind1 == Kind.METHOD_CALL)
            {
                Kind kindthis = Kind.fromString(node.getChild(0).getChild(0).getKind());
                if(kindthis  != Kind.THIS)
                {
                    var caller = node.getChild(0).getChild(0).get("var");
                    if(imp.contains(caller))
                    {
                        return null;
                    }
                    else {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                node.getLine(),
                                node.getColumn(),
                                "Index is not an integer",
                                null)
                        );
                    }
                }
                else {
                    return null;
                }
            }
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Index was not declared",
                    null)
            );
        }
        return null;
    }

}
