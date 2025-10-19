package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }

    @Override
    protected void buildVisitor() {

        addVisit(BINARY_OP, this::visitBinExpr);
        addVisit(ARRAY_INDEX, this::visitArrayIndex);
        addVisit(ARRAY_LENGTH, this::visitArrayLength);
        addVisit(NEW_ARRAY, this::visitNewArray);
        addVisit(LOGICAL_NOT, this::visitNot);
        addVisit(PARENTHESES_OP, this::visitParentheses);
        addVisit(INTEGER, this::visitInteger);
        addVisit(TRUE, this::visitTrue);
        addVisit(FALSE, this::visitFalse);
        addVisit(IDENTIFIER, this::visitVarRef);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(NEW_OBJECT, this::visitNewObject);
        addVisit(ARRAY_LITERAL, this::visitArrayLiteral);
        addVisit(THIS, this::visitThisExpr);
        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        String op = node.get("op");

        // Special case for short-circuit evaluation of logical AND
        if (op.equals("&&")) {
            return generateShortCircuitAnd(node);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // reference to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // reference to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String reference = ollirTypes.nextTemp() + resOllirType;

        computation.append(reference).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getReference()).append(SPACE);

        Type type = types.getExprType(node);
        computation.append(op).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getReference()).append(END_STMT);

        return new OllirExprResult(reference, computation);
    }

    /**
     * Generates OLLIR code for short-circuit evaluation of logical AND.
     * This implements the pattern:
     *    if (!left) goto false;
     *    result = right;
     *    goto end;
     * false:
     *    result = false;
     * end:
     */
    private OllirExprResult generateShortCircuitAnd(JmmNode node) {
        // Process left side of the AND
        var lhs = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());

        // Generate label names
        String falseLabel = "and_false_" + ollirTypes.nextLabelId();
        String endLabel = "and_end_" + ollirTypes.nextLabelId();

        // Define result variable
        Type boolType = TypeUtils.newBoolType();
        String boolOllirType = ollirTypes.toOllirType(boolType);
        String resultVar = ollirTypes.nextTemp() + boolOllirType;

        // If left side is false, jump to falseLabel
        computation.append("if (!").append(boolOllirType).append(" ").append(lhs.getReference())
                .append(") goto ").append(falseLabel).append(";\n");

        // Process right side (only if left side is true)
        var rhs = visit(node.getChild(1));
        computation.append(rhs.getComputation());
        computation.append(resultVar).append(" ").append(ASSIGN).append(boolOllirType).append(" ")
                .append(rhs.getReference()).append(";\n");
        computation.append("goto ").append(endLabel).append(";\n");

        // False case (left was false, so result is false)
        computation.append(falseLabel).append(":\n");
        computation.append(resultVar).append(" ").append(ASSIGN).append(boolOllirType).append(" 0").append(boolOllirType).append(";\n");

        // End label
        computation.append(endLabel).append(":\n");

        return new OllirExprResult(resultVar, computation);
    }

    private OllirExprResult visitArrayIndex(JmmNode node, Void unused) {

        // arr[10]
        //tmp0.i32 :=.i32 arr.array.i32[10.i32].i32;

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));
        StringBuilder computation = new StringBuilder();

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        var rhsType = types.getExprType(node);
        String rhsOllirType = ollirTypes.toOllirType(rhsType);

        String reference = ollirTypes.nextTemp() + rhsOllirType;

        computation.append(reference).append(SPACE).append(ASSIGN).append(rhsOllirType).append(SPACE)
                .append(lhs.getReference()).append("[").append(rhs.getReference()).append("]")
                .append(rhsOllirType).append(END_STMT);

        return new OllirExprResult(reference, computation);
    }


    private OllirExprResult visitArrayLength(JmmNode node, Void unused) {

        //arraylength(arr.array.i32).i32;

        var rhs = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();

        computation.append(rhs.getComputation());

        var rhsType = types.getExprType(node);
        String rhsOllirType = ollirTypes.toOllirType(rhsType);

        String reference = ollirTypes.nextTemp() + rhsOllirType;

        computation.append(reference).append(SPACE).append(ASSIGN).append(rhsOllirType).append(SPACE)
                .append("arraylength(").append(rhs.getReference()).append(").i32;\n");

        return new OllirExprResult(reference, computation);
    }


    private OllirExprResult visitNewArray(JmmNode node, Void unused) {

        var rhs = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();

        computation.append(rhs.getComputation());
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);

        String reference = ollirTypes.nextTemp() + resOllirType;

        //new(array, 20.i32).array.i32;

        computation.append(reference).append(SPACE).append(ASSIGN).append(resOllirType)
                .append(SPACE).append("new(array, ").append(rhs.getReference()).append(")").append(resOllirType)
                .append(END_STMT);

        return new OllirExprResult(reference, computation);
    }


    private OllirExprResult visitNot(JmmNode node, Void unused) {

        System.out.println(node);
        var rhs = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();

        // reference to compute the children
        computation.append(rhs.getComputation());

        // reference to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String reference = ollirTypes.nextTemp() + resOllirType;

        computation.append(reference).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("!").append(resOllirType).append(SPACE)
                .append(rhs.getReference()).append(END_STMT);

        return new OllirExprResult(reference, computation);
    }


    private OllirExprResult visitParentheses(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitTrue(JmmNode node, Void unused) {
        return new OllirExprResult("1.bool");
    }


    private OllirExprResult visitFalse(JmmNode node, Void unused) {
        return new OllirExprResult("0.bool");
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var methodNode = node.getAncestor(METHOD_DECL).orElse(null);
        if (methodNode == null) {
            return new OllirExprResult("", "Error: Could not find method declaration");
        }

        String currentMethod = methodNode.get("methodName");
        var types = new TypeUtils(table, currentMethod);
        String varName = node.get("var");
        Type varType = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(varType);

        // Check if this is a field (and not a local var or parameter)
        boolean isField = isField(varName, currentMethod);

        if (isField) {
            // If it's a field, generate getfield instruction
            String tempVar = ollirTypes.nextTemp() + ollirType;
            StringBuilder computation = new StringBuilder();

            // Generate getfield instruction
            computation.append(tempVar).append(" ").append(ASSIGN).append(ollirType)
                    .append(" getfield(this, ").append(varName).append(ollirType).append(")").append(ollirType).append(END_STMT);

            return new OllirExprResult(tempVar, computation);
        } else {
            // For local variables and parameters, just return the variable with its type
            return new OllirExprResult(varName + ollirType);
        }
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

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String methodName = node.get("methodName");

        JmmNode callerNode = node.getChild(0);
        OllirExprResult callerRes = visit(callerNode);
        computation.append(callerRes.getComputation());

        List<String> argRefs = new ArrayList<>();
        for (int i = 1; i < node.getNumChildren(); i++) {
            OllirExprResult argRes = visit(node.getChild(i));
            computation.append(argRes.getComputation());
            argRefs.add(argRes.getReference());
        }

        Type retType = types.getExprType(node);
        if (retType == null) retType = new Type("void", false);
        String ollirRetType = ollirTypes.toOllirType(retType);
        boolean isVoid = ".V".equals(ollirRetType);
        //esta com problemas
        String callType;
        String typedCaller;

        if (callerNode.getKind().equals("Identifier")) {
            String callerName = callerNode.get("var");
            if (table.getImports().contains(callerName)) {
                // O ID está presente no import? invokestatic
                callType = "invokestatic";
                typedCaller = callerName;
            } else {
                // O ID não está presente nos imports -> é um metodo local logo não é static (só a main é static)
                callType = "invokevirtual";
                Type callerType = types.getExprType(callerNode);
                // Caso o caller já contem um . (por exemplo d.ComplexArrayAcess) então o tipo é a class e não o return type.
                if(callerRes.getReference().contains(".")){
                    typedCaller = callerRes.getReference();
                }
                else
                    typedCaller = callerRes.getReference() + ollirTypes.toOllirType(callerType);
            }
        } else {
            // Caso o caller não seja um identifier é so ver o tipo e a referência associada
            callType = "invokevirtual";
            Type callerType = types.getExprType(callerNode);
            typedCaller = callerRes.getReference();
        }

        StringBuilder call = new StringBuilder();
        call.append(callType).append("(").append(typedCaller).append(", \"").append(methodName).append("\"");

        for (String argRef : argRefs) {
            call.append(", ").append(argRef);
        }

        call.append(")").append(ollirRetType).append(END_STMT);

        if (isVoid) {
            computation.append(call);
            return new OllirExprResult("", computation);
        } else {
            String temp = ollirTypes.nextTemp() + ollirRetType;
            computation.append(temp).append(SPACE).append(ASSIGN).append(ollirRetType)
                    .append(SPACE).append(call);
            return new OllirExprResult(temp, computation);
        }
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {
        String className = node.get("className");
        String ollirType = "." + className;

        String tempVar = ollirTypes.nextTemp() + ollirType;
        StringBuilder computation = new StringBuilder();

        computation.append(tempVar).append(" :=.").append(className)
                .append(" new(").append(className).append(").").append(className).append(";\n");

        var invoke =table.getImports().contains(className) ? "invokestatic(" : "invokespecial(" ;
        computation.append(invoke).append(tempVar).append(", \"<init>\").V;\n");

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitArrayLiteral(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        List<JmmNode> elements = node.getChildren(); // assuming each child is a literal

        int size = elements.size();
        String temp = ollirTypes.nextTemp("tmp") + ".array.i32";

        // arrays are always integers (project specification)
        computation.append(temp).append(" :=.array.i32 new(array, ")
                .append(size).append(".i32).array.i32;\n");

        int i=0;
        for(var child : node.getChildren())
        {
            OllirExprResult childRes = visit(child);
            computation.append(temp)
                    .append("[")
                    .append(i).append(".i32].i32 :=.i32 ")
                    .append(childRes.getReference()).append(";\n");
            i++;
        }

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitThisExpr(JmmNode node, Void unused) {
        String className = table.getClassName();
        String ref = "this." + className;
        return new OllirExprResult(ref, "");
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }
}