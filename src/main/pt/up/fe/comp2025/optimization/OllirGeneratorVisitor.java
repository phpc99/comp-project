package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private final String L_PARENTHESIS = "(";
    private final String R_PARENTHESIS = ")";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;
    private int if_counter = 0;
    private int while_counter = 0; // added


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt); // added
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        setDefaultVisit(this::defaultVisit);
    }


    private String visitExprStmt(JmmNode node, Void unused) {
        OllirExprResult result = exprVisitor.visit(node.getChild(0));
        return result.getComputation();
    }
    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }


    private String visitImportDecl(JmmNode node, Void unused) {

        return "import " +
                String.join(".", node.getObjectAsList("importName", String.class)) +
                END_STMT;
    }



    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append("\n");
        code.append(table.getClassName());

        if (table.getSuper() != null) {
            code.append(" extends ");
            code.append(table.getSuper());
        }

        code.append(" {\n\n");

        for (var child : node.getChildren(VAR_DECL)) {
            var type = ollirTypes.toOllirType(types.getFieldType(child.get("var")));

            code.append(".field public ")
                    .append(child.get("var")).append(type)
                    .append(END_STMT).append("\n");
        }

        code.append(buildConstructor());
        code.append("\n");

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append("}\n");

        return code.toString();
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        code.append(".method ");
        var method_name = node.get("methodName");
        if (node.get("isPublic").equals("true")) {
            code.append("public ");
        }
        if (node.get("isStatic").equals("true")) {
            code.append("static ");
        }
        //The vararg parameter can only be the last. The analysis already checks if this is true otherwise it doesn't get to this phase.

        var hasVararg = !table.getParameters(method_name).isEmpty() ? table.getParameters(method_name).getLast().getType().getName().equals("...") : false;
        if(hasVararg)
        {
            code.append("varargs ");
        }
        code.append(method_name);

        code.append(L_PARENTHESIS);
        for(var param : table.getParameters(method_name))
        {
            var paramCode = ollirTypes.toOllirType(param.getType());
            if (table.getParameters(method_name).indexOf(param) == table.getParameters(method_name).size() - 1) {
                code.append(param.getName() + paramCode);
            } else {
                code.append(param.getName() + paramCode + ", ");
            }
        }
        code.append(R_PARENTHESIS);

        var retType = ollirTypes.toOllirType(table.getReturnType(method_name));
        code.append(retType);
        code.append(L_BRACKET);

        // rest of its children stmts
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);

        if (node.hasAttribute("hasReturn")) {
            var retExpr = node.getChild(node.getChildren().size() - 1);
            var type = ollirTypes.toOllirType(table.getReturnType(node.get("methodName")));
            var ret = exprVisitor.visit(retExpr);
            code.append(ret.getComputation());

            code.append("ret" + type + SPACE + ret.getReference() + END_STMT);
        }
        else
        {
            code.append("ret.V");
            code.append(END_STMT);
        }
        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitBlockStmt(JmmNode node, Void unused) {

        var code = new StringBuilder();

        for (var child: node.getChildren())
            code.append(visit(child, unused));

        return code.toString();
    }


    private String visitIfStmt(JmmNode node, Void unused) {

        /*
        * if (true) {
            index = 1;
        } else {
            index = 2;
        }
        *
        * tmp0 = true;
        * index = 1;
        * index = 2;
        *
        * */

        if_counter++;

        var cond = exprVisitor.visit(node.getChild(0));
        var thenStmt = visit(node.getChild(1));
        var elseStmt = visit(node.getChild(2));

        System.out.println(cond);
        System.out.println(thenStmt);
        System.out.println(elseStmt);

        var code = new StringBuilder();

        code.append(cond.getComputation());
        code.append("if (").append(cond.getReference()).append(") ").append("goto Then" + if_counter + ";\n");
        code.append(elseStmt);
        code.append("goto End" + if_counter + ";\n");
        code.append("Then" + if_counter + ": \n").append(thenStmt);
        code.append("End" + if_counter + ":\n");

        return code.toString();
    }


    private String visitWhileStmt(JmmNode node, Void unused) {

        while_counter++;

        var cond = exprVisitor.visit(node.getChild(0));
        var thenStmt = visit(node.getChild(1));
        //var elseStmt = visit(node.getChild(2));

        System.out.println(cond);
        System.out.println(thenStmt);
        //System.out.println(elseStmt);

        var code = new StringBuilder();

        code.append(cond.getComputation());
        code.append("Cond" + while_counter + ":\n"); // added
        code.append("if (").append(cond.getReference().replace(".i32", ".bool")).append(") ").append("goto Then" + while_counter + ";\n");
        code.append("goto End" + while_counter + ";\n");
        code.append("Then" + while_counter + ": \n").append(thenStmt);
        code.append("goto Cond" + while_counter + ";\n"); // added
        code.append("End" + while_counter + ":\n");

        return code.toString();
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        var rhs = exprVisitor.visit(node.getChild(0));

        StringBuilder code = new StringBuilder();

        code.append(rhs.getComputation());

        // Get the variable name
        String varName = node.get("var");

        // Get the current method name
        String methodName = node.getAncestor(METHOD_DECL).orElseThrow().get("methodName");

        // Check if this is a field assignment
        boolean isField = isField(varName, methodName);

        if (isField) {
            // Generate putfield instruction for field assignment
            var types = new TypeUtils(table, methodName);
            String typeString = ollirTypes.toOllirType(types.getExprType(node.getChild(0)));

            code.append("putfield(this, ")
                    .append(varName).append(typeString)
                    .append(", ")
                    .append(rhs.getReference())
                    .append(")").append(typeString)
                    .append(END_STMT);
        } else {
            // Normal local variable assignment
            var types = new TypeUtils(table, methodName);
            String typeString = ollirTypes.toOllirType(types.getExprType(node.getChild(0)));
            var varCode = varName + typeString;

            code.append(varCode);
            code.append(SPACE);
            code.append(ASSIGN);
            code.append(typeString);
            code.append(SPACE);
            code.append(rhs.getReference());
            code.append(END_STMT);
        }

        return code.toString();
    }

    /**
     * Determines if a variable is a field (and not a local variable or parameter).
     *
     * @param varName Name of the variable
     * @param methodName Name of the current method
     * @return true if the variable is a field, false otherwise
     */
    private boolean isField(String varName, String methodName) {
        // Check if the variable is a parameter
        if (table.getParameters(methodName) != null) {
            for (var param : table.getParameters(methodName)) {
                if (param.getName().equals(varName)) {
                    return false;
                }
            }
        }

        // Check if the variable is a local variable
        if (table.getLocalVariables(methodName) != null) {
            for (var local : table.getLocalVariables(methodName)) {
                if (local.getName().equals(varName)) {
                    return false;
                }
            }
        }

        // Check if the variable is a field
        if (table.getFields() != null) {
            for (var field : table.getFields()) {
                if (field.getName().equals(varName)) {
                    return true;
                }
            }
        }

        return false;
    }


    private String visitArrayAssignStmt(JmmNode node, Void unused) {

        /*
         * arr[index + 3] = 1 + 3;
         *
         * tmp0.i32 :=.i32 index.i32 +.i32 3.i32;    Done
         * tmp1.i32 :=.i32 1.i32 +.i32 3.i32;        Done
         * arr[tmp0.i32].i32 :=.i32 tmp1.i32;
         * */

        var arrayName = node.get("var");
        var indexExpr = exprVisitor.visit(node.getChild(0));
        var rhsExpr = exprVisitor.visit(node.getChild(1));

        StringBuilder code = new StringBuilder();

        code.append(indexExpr.getComputation());
        code.append(rhsExpr.getComputation());

        var types = new TypeUtils(table, node.getAncestor(METHOD_DECL).get().get("methodName"));
        String typeString = ollirTypes.toOllirType(types.getExprType(node.getChild(1)));

        code.append(arrayName).append("[").append(indexExpr.getReference()).append("]")
                .append(typeString).append(SPACE).append(ASSIGN).append(typeString)
                .append(SPACE).append(rhsExpr.getReference()).append(END_STMT);

        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }


    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}