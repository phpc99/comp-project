package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class AssignmentCheckType extends AnalysisVisitor{
    private TypeUtils typeUtils;

    @Override
    public void buildVisitor()
    {
        addVisit(Kind.ASSIGN_STMT,this::visitAssignment);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }
    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        typeUtils = new TypeUtils(table, method.get("methodName"));
        return null;
    }
    private Void visitAssignment(JmmNode node, SymbolTable table) {
        SpecsCheck.checkNotNull(typeUtils, () -> "Expected current method to be set");
        var variable = node.get("var");
        var expression = node.getChild(0);
        var varType = typeUtils.getVarType(variable);
        var exprType = typeUtils.getExprType(expression);

        var imports = table.getImports();
        boolean varImp = imports.contains(varType.getName());
        boolean expImp = imports.contains(exprType.getName());

        if(varImp && expImp)
        {
            return null;
        }

        if (!TypeUtils.isCompatibleType(varType, exprType, table) || (varType.isArray() != exprType.isArray())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Type mismatch: cannot assign " + exprType.getName() + " to " + varType.getName(),
                    null)
            );
        }

        return null;
    }

}