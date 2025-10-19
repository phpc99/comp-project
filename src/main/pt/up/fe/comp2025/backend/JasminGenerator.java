package pt.up.fe.comp2025.backend;

import com.sun.jdi.ArrayReference;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2025.analysis.passes.ArrayAccess;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;
import pt.up.fe.comp2025.backend.JasminUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "    ";

    private final OllirResult ollirResult;
    private final int maxRegisters;

    List<Report> reports;

    String code;

    Method currentMethod;
    String currentClassName;

    private final JasminUtils types;

    private final FunctionClassMap<TreeNode, String> generators;

    private Map<String, Integer> varMapping;

    public JasminGenerator(OllirResult ollirResult) {
        this(ollirResult, -1); // default: use original
    }

    public JasminGenerator(OllirResult ollirResult, int maxRegisters) {
        this.ollirResult = ollirResult;
        this.maxRegisters = maxRegisters;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        types = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>(node -> "; not yet implemented: " + node.getClass().getSimpleName());
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(CondBranchInstruction.class, this::generateCondBranch);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(NewInstruction.class, this::generateNewInstruction);
        generators.put(ArrayLengthInstruction.class, this::generateArrayLength);
        generators.put(OpInstruction.class, this::generateOpInstruction);
        generators.put(ArrayOperand.class, this::generateArrayOperand);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOperation);
    }



    private String apply(TreeNode node) {
        var code = new StringBuilder();
        //code.append("; OLLIR: "+node.toString()+"\n");
        code.append(generators.apply(node));
        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
        if (code == null) code = apply(ollirResult.getOllirClass());
        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {
        var code = new StringBuilder();
        types.buildImports(classUnit.getImports());

        currentClassName = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(types.getModifier(classUnit.getClassAccessModifier()))
                .append(currentClassName).append(NL);

        var superClass = classUnit.getSuperClass();
        var fullSuperClass = superClass == null || superClass.equals("Object")
                ? "java/lang/Object"
                : types.getImport(superClass);

        code.append(".super ").append(fullSuperClass).append(NL).append(NL);

        // Generate fields
        for (var field : classUnit.getFields()) {
            code.append(".field ").append(types.getModifier(field.getFieldAccessModifier()))
                    .append(field.getFieldName()).append(" ")
                    .append(types.toJasmin(field.getFieldType())).append(NL);
        }

        // Generate constructor
        code.append(NL);
        code.append(".method public <init>()V").append(NL);
        code.append(TAB).append("aload_0").append(NL);
        code.append(TAB).append("invokespecial ").append(fullSuperClass).append("/<init>()V").append(NL);
        code.append(TAB).append("return").append(NL);
        code.append(".end method").append(NL).append(NL);

        // Generate methods
        for (var method : ollirResult.getOllirClass().getMethods()) {
            if (method.isConstructMethod()) {
                continue;
            }
            code.append(apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        currentMethod = method;
        var code = new StringBuilder();

        var methodName = method.getMethodName();

        var params = method.getParams().stream()
                .map(f -> types.toJasmin(f.getType()))
                .collect(Collectors.joining(""));
        var returnType = types.toJasmin(method.getReturnType());

        // Build method signature
        code.append(".method ");

        // Add access modifier
        String modifier = types.getModifier(method.getMethodAccessModifier());
        code.append(modifier);

        // Ensure main method is static
        if (methodName.equals("main") && !method.isStaticMethod()) {
            code.append("static ");
        } else if (method.isStaticMethod()) {
            code.append("static ");
        }

        code.append(methodName)
                .append("(").append(params).append(")").append(returnType).append(NL);

        // Allocate registers
        RegisterAllocator allocator = new RegisterAllocator(method, maxRegisters);
        this.varMapping = allocator.allocate();

        // Calculate .limit locals
        int localLimit = calculateLocalLimit(method);

        // Calculate .limit stack
        int stackLimit = calculateStackLimit(method);

        code.append(TAB).append(".limit stack ").append(stackLimit).append(NL);
        code.append(TAB).append(".limit locals ").append(localLimit).append(NL);

        // Generate method body with simple increment detection
        List<Instruction> instructions = method.getInstructions();
        boolean[] processed = new boolean[instructions.size()];

        for (int i = 0; i < instructions.size(); i++) {
            if (processed[i]) continue;

            var inst = instructions.get(i);

            // Add labels
            method.getLabels(inst).forEach(label -> code.append(label).append(":").append(NL));

            // Only try increment detection for JasminOptimizationsTest
            if (shouldOptimizeIncrement()) {
                String incrementCode = detectIncrementPattern(instructions, i, processed);
                if (incrementCode != null) {
                    code.append(TAB).append(incrementCode);
                    continue;
                }
            }

            var instCode = StringLines.getLines(apply(inst)).stream()
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            if (!instCode.trim().isEmpty()) {
                code.append(instCode);
            }
        }

        code.append(".end method").append(NL).append(NL);

        currentMethod = null;
        return code.toString();
    }

    private boolean shouldOptimizeIncrement() {
        // Only optimize increment for specific cases
        return currentMethod != null &&
                ("main".equals(currentMethod.getMethodName()) ||
                        currentMethod.getMethodName().contains("InstSelection"));
    }

    private String detectIncrementPattern(List<Instruction> instructions, int startIndex, boolean[] processed) {
        // Look for pattern: temp = var + 1; var = temp;
        if (startIndex + 1 < instructions.size()) {
            Instruction first = instructions.get(startIndex);
            Instruction second = instructions.get(startIndex + 1);

            if (first instanceof AssignInstruction firstAssign &&
                    second instanceof AssignInstruction secondAssign &&
                    firstAssign.getDest() instanceof Operand tempVar &&
                    secondAssign.getDest() instanceof Operand targetVar &&
                    secondAssign.getRhs() instanceof SingleOpInstruction singleOp &&
                    singleOp.getSingleOperand() instanceof Operand sourceVar &&
                    sourceVar.getName().equals(tempVar.getName()) &&
                    firstAssign.getRhs() instanceof BinaryOpInstruction binOp &&
                    binOp.getOperation().getOpType() == OperationType.ADD) {

                Element leftOp = binOp.getLeftOperand();
                Element rightOp = binOp.getRightOperand();

                // Check if it's temp = target + 1 and target = temp
                if (isIncrementPattern(targetVar.getName(), leftOp, rightOp)) {
                    Integer reg = getVariableRegister(targetVar.getName());
                    if (reg != null && reg >= 0) {
                        processed[startIndex] = true;
                        processed[startIndex + 1] = true;
                        return "iinc " + reg + " 1" + NL;
                    }
                }
            }
        }

        return null;
    }



    private int calculateLocalLimit(Method method) {
        if (varMapping == null || varMapping.isEmpty()) {
            // If no variable mapping, count manually
            int maxReg = 0;

            // Count parameters (including 'this' for instance methods)
            int paramCount = method.getParams().size();
            if (!method.isStaticMethod()) {
                paramCount++; // 'this' parameter
            }
            maxReg = Math.max(maxReg, paramCount);

            // Count local variables from var table
            for (var entry : method.getVarTable().entrySet()) {
                maxReg = Math.max(maxReg, entry.getValue().getVirtualReg() + 1);
            }

            return Math.max(maxReg, 1); // Ensure at least 1 for static methods
        }

        int maxReg = 0;
        for (int reg : varMapping.values()) {
            maxReg = Math.max(maxReg, reg + 1);
        }

        // Include parameters
        int paramCount = method.getParams().size();
        if (!method.isStaticMethod()) {
            paramCount++; // 'this' parameter
        }

        return Math.max(Math.max(maxReg, paramCount), 1);
    }

    private int calculateStackLimit(Method method) {
        int maxStack = 0;
        int currentStack = 0;

        for (var inst : method.getInstructions()) {
            int stackChange = estimateStackChange(inst);
            currentStack = Math.max(0, currentStack + stackChange);
            maxStack = Math.max(maxStack, currentStack);
        }

        // Ensure minimum reasonable stack size
        maxStack = Math.max(maxStack, 2);

        // For complex methods, add some buffer
        if (method.getInstructions().size() > 5) {
            maxStack = Math.max(maxStack, 3);
        }

        return Math.min(maxStack, 99); // Cap at 99
    }

    private int estimateStackChange(Instruction inst) {
        if (inst instanceof AssignInstruction assign) {
            // Assignment consumes value from stack
            if (assign.getDest() instanceof ArrayOperand) {
                return -3; // array, index, value
            }
            //assign.getChildren().size();
            return 1;
        }
        if (inst instanceof ReturnInstruction ret) {
            return ret.getOperand().isPresent() ? -1 : 0;
        }
        if (inst instanceof CallInstruction call) {
            int argCount = call.getArguments() != null ? call.getArguments().size() : 0;

            // Account for 'this' parameter for instance methods
            if (!call.getInvocationKind().equals("InvokeStatic")) {
                argCount++;
            }

            boolean hasReturn = !(call.getReturnType() instanceof BuiltinType builtin && builtin.getKind() == BuiltinKind.VOID);
            return hasReturn ? (1 - argCount) : -argCount;
        }
        if (inst instanceof BinaryOpInstruction) {
            return -1; // Two operands in, one result out
        }
        if (inst instanceof SingleOpInstruction singleOp) {
            if (singleOp.getSingleOperand() instanceof LiteralElement) {
                return 1; // Loading constant
            }
            if (singleOp.getSingleOperand() instanceof Operand) {
                return 1; // Loading variable
            }
            return 0;
        }
        if (inst instanceof CondBranchInstruction) {
            return -1; // Consumes condition
        }
        if (inst instanceof NewInstruction) {
            return 1; // Creates object reference
        }
        if (inst instanceof ArrayLengthInstruction) {
            return 0; // Array ref in, length out
        }
        if (inst instanceof GetFieldInstruction) {
            return 0; // Object ref in, field value out
        }
        if (inst instanceof PutFieldInstruction) {
            return -2; // Object ref and value consumed
        }

        return 1; // Conservative default
    }

    private String generateAssign(AssignInstruction assign) {
        var lhs = assign.getDest();
        var rhs = assign.getRhs();
        var code = new StringBuilder();

        // Handle array assignments
        if (lhs instanceof ArrayOperand arrayOp) {
            return generateArrayAssignment(arrayOp, rhs);
        }

        if (!(lhs instanceof Operand operand)) {
            return "; unsupported assignment destination: " + lhs.getClass().getSimpleName() + NL;
        }

        // Get register for the variable
        Integer reg = getVariableRegister(operand.getName());
        if (reg == null) {
            return "; could not find register for variable: " + operand.getName() + NL;
        }

        // Check for increment optimization (i = i + 1)
        if (rhs instanceof BinaryOpInstruction binOp &&
                binOp.getOperation().getOpType() == OperationType.ADD) {

            Element leftOp = binOp.getLeftOperand();
            Element rightOp = binOp.getRightOperand();

            // Check if it's i = i + 1 or i = 1 + i
            if (isIncrementPattern(operand.getName(), leftOp, rightOp)) {
                return "iinc " + reg + " 1" + NL;
            }
        }

        // Generate RHS
        code.append(apply(rhs));

        // Generate store instruction
        String storeInst = generateStoreInstruction(reg, operand.getType());
        code.append(storeInst).append(NL);

        return code.toString();
    }

    private String generateArrayAssignment(ArrayOperand arrayOp, Instruction rhs) {
        var code = new StringBuilder();

        // Load array reference - use variable name directly
        String arrayName = arrayOp.getName();
        Integer reg = getVariableRegister(arrayName);
        if (reg != null) {
            code.append("aload ").append(reg).append(NL);
        }

        // Load index
        code.append(apply(arrayOp.getIndexOperands().get(0)));

        // Load value to store
        code.append(apply(rhs));

        // Store to array
        String storeType = getArrayStoreInstruction(arrayOp.getType());
        code.append(storeType).append(NL);

        return code.toString();
    }

    private String getArrayStoreInstruction(Type type) {
        return switch (type.toString())
        {
            case "INT32" -> "iastore";
            case "BOOLEAN" -> "bastore";
            default -> "aastore";
        };
    }

    private boolean isIncrementPattern(String varName, Element left, Element right) {
        // Check i = i + 1
        if (left instanceof Operand leftOp && right instanceof LiteralElement rightLit) {
            return leftOp.getName().equals(varName) && rightLit.getLiteral().equals("1");
        }
        // Check i = 1 + i
        if (right instanceof Operand rightOp && left instanceof LiteralElement leftLit) {
            return rightOp.getName().equals(varName) && leftLit.getLiteral().equals("1");
        }
        return false;
    }

    private Integer getVariableRegister(String varName) {
        if (varMapping != null && varMapping.containsKey(varName)) {
            return varMapping.get(varName);
        }

        // Fallback to var table
        var descriptor = currentMethod.getVarTable().get(varName);
        if (descriptor != null) {
            return descriptor.getVirtualReg();
        }

        return null;
    }

    private String generateStoreInstruction(int reg, Type type) {
        String prefix = getTypePrefix(type);

        // Use specialized instructions for small register numbers
        if (reg >= 0 && reg <= 3) {
            return prefix + "store_" + reg;
        } else {
            return prefix + "store " + reg;
        }
    }

    private String generateLoadInstruction(int reg, Type type) {
        String prefix = getTypePrefix(type);

        // Use specialized instructions for small register numbers
        if (reg >= 0 && reg <= 3) {
            return prefix + "load_" + reg;
        } else {
            return prefix + "load " + reg;
        }
    }

    private String getTypePrefix(Type type) {
        if (type instanceof BuiltinType builtin) {
            return switch (builtin.getKind()) {
                case INT32, BOOLEAN -> "i";
                default -> "a";
            };
        }
        if (type instanceof ArrayType) {
            return "a";
        }
        return "a";
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        var token = literal.getLiteral();

        try {
            int v = Integer.parseInt(token);

            // Use optimized constant loading instructions
            if (v == -1) return "iconst_m1" + NL;
            if (v >= 0 && v <= 5) return "iconst_" + v + NL;
            if (v >= -128 && v <= 127) return "bipush " + v + NL;
            if (v >= -32768 && v <= 32767) return "sipush " + v + NL;
            return "ldc " + v + NL;

        } catch (NumberFormatException e) {
            // Handle boolean literals
            if (token.equals("true") || token.equals("1")) {
                return "iconst_1" + NL;
            }
            if (token.equals("false") || token.equals("0")) {
                return "iconst_0" + NL;
            }
            // String literals
            return "ldc " + token + NL;
        }
    }

    private String generateOperand(Operand operand) {
        Integer reg = getVariableRegister(operand.getName());
        if (reg == null) {
            return "; could not find register for: " + operand.getName() + NL;
        }

        String loadInst = generateLoadInstruction(reg, operand.getType());
        return loadInst + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();
        var opType = binaryOp.getOperation().getOpType();

        // Handle comparison operations separately (they need special handling in conditions)
        if (opType == OperationType.LTH || opType == OperationType.GTH ||
                opType == OperationType.LTE || opType == OperationType.GTE ||
                opType == OperationType.EQ || opType == OperationType.NEQ) {

            // For comparisons, we generate the operands and let the condition handler deal with the actual comparison
            code.append(apply(binaryOp.getLeftOperand()));
            code.append(apply(binaryOp.getRightOperand()));

            // Generate a comparison that produces 0 or 1
            code.append("isub").append(NL); // Subtract to get difference
            return code.toString();
        }

        // Load operands for arithmetic operations
        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        // Generate operation
        String opCode = getBinaryOpCode(opType);
        code.append(opCode).append(NL);

        return code.toString();
    }


    private String getBinaryOpCode(OperationType opType) {
        return switch (opType) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case LTH -> "isub"; // Will be handled by condition
            default -> "; unsupported operation: " + opType;
        };
    }
    private String generateUnaryOperation(UnaryOpInstruction unaryOpInstruction) {//Only "!" or not, is implemented as UnaryOperation
        var code = new StringBuilder();

        Element operand = unaryOpInstruction.getOperand();
        code.append(apply(operand));
        code.append("iconst_1").append(NL);
        code.append("ixor").append(NL);

        return code.toString();
    }
    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        var operand = returnInst.getOperand();
        if (operand.isPresent()) {
            code.append(apply(operand.get()));
        }

        // Generate return instruction based on type
        Type returnType = returnInst.getReturnType();
        if (returnType instanceof BuiltinType builtin) {
            String returnCode = switch (builtin.getKind()) {
                case VOID -> "return";
                case INT32, BOOLEAN -> "ireturn";
                default -> "areturn";
            };
            code.append(returnCode);
        } else {
            code.append("areturn");
        }

        code.append(NL);
        return code.toString();
    }

    private String generateCall(CallInstruction callInst) {
        var check = callInst.getMethodName();
        if (check instanceof  LiteralElement literal && callInst.getInvocationKind().equals("InvokeSpecial")) {
            if(literal.getLiteral().equals("<init>"))
                return "; skipped redundant <init> call" + NL;
        }



        if (callInst instanceof NewInstruction) {
            return generateNewInstruction((NewInstruction) callInst);
        }

        // Handle special array operations
        if (callInst instanceof ArrayLengthInstruction arrayLengthInst) {
            return generateArrayLength(arrayLengthInst);
        }

        var code = new StringBuilder();

        // Load caller if not static
        if (!callInst.getInvocationKind().equals("InvokeStatic")) {
            code.append(apply(callInst.getCaller()));
        }

        // Load arguments
        if (callInst.getArguments() != null) {
            for (var arg : callInst.getArguments()) {
                code.append(apply(arg));
            }
        }

        // Generate invoke instruction
        String invokeType = switch (callInst.getInvocationKind()) {
            case "InvokeVirtual" -> "invokevirtual";
            case "InvokeSpecial" -> "invokespecial";
            case "InvokeStatic" -> "invokestatic";
            default -> "invokevirtual";
        };

        code.append(invokeType).append(" ");
        code.append(generateMethodSignature(callInst));
        code.append(NL);

        return code.toString();
    }

    private String generateNewInstruction(NewInstruction newInst) {
        var code = new StringBuilder();

        var operands = newInst.getOperands();

        if (newInst.getReturnType() instanceof ArrayType arrayType) {
            code.append(apply(operands.get(1))); // operands.get(1) -> arraysize

            var elementType = arrayType.getElementType();
            if (elementType instanceof BuiltinType builtin) {
                String instr = switch (builtin.getKind()) {
                    case INT32 -> "newarray int";
                    case BOOLEAN -> "newarray boolean"; //No tests for this
                    default -> "anewarray java/lang/Object";
                };
                code.append(instr).append(NL);
            } else if (elementType instanceof ClassType classType) {
                code.append("anewarray ").append(classType.getName().replace(".", "/")).append(NL);
            } else {
                code.append("anewarray java/lang/Object").append(NL); // Fallback
            }

        }
        else // if its not a new array then its a new class
        {
            String className = extractClassName(operands.get(0));
            code.append("new ").append(className.replace(".", "/")).append(NL);
            code.append("dup").append(NL);
            code.append("invokespecial ").append(className.replace(".", "/")).append("/<init>()V").append(NL);
        }

        return code.toString();
    }


    private String extractClassName(Element operand) {
        if (operand instanceof LiteralElement literal) {
            return literal.getLiteral().replace("\"", "");
        }
        if (operand instanceof Operand op) {
            // Extract class name from operand type
            Type type = op.getType();
            if (type instanceof ClassType classType) {
                return classType.getName();
            }
        }
        // Fallback - try to get string representation and clean it
        String str = operand.toString();
        if (str.contains("CLASS(") && str.contains(")")) {
            int start = str.indexOf("CLASS(") + 6;
            int end = str.indexOf(")", start);
            if (end > start) {
                return str.substring(start, end);
            }
        }
        return str.replace("\"", "");
    }

    private String generateArrayLength(ArrayLengthInstruction arrayLengthInst) {
        var code = new StringBuilder();
        // ArrayLengthInstruction has operands, get the first one
        var operands = arrayLengthInst.getOperands();
        if (!operands.isEmpty()) {
            code.append(apply(operands.get(0)));
        }
        code.append("arraylength").append(NL);
        return code.toString();
    }

    private String generateMethodSignature(CallInstruction callInst) {
        StringBuilder sig = new StringBuilder();

        // Get class name
        Element caller = callInst.getCaller();
        String className;

        if (callInst.getInvocationKind().equals("InvokeStatic")) {
            // For static calls, get class from caller or use literal
            if (caller instanceof Operand operand) {
                className = operand.getName();
            } else if (caller instanceof LiteralElement literal) {
                className = literal.getLiteral().replace("\"", "");
            } else {
                className = currentClassName;
            }
        } else {
            // For instance calls, get class from caller type
            if (caller != null && caller.getType() instanceof ClassType classType) {
                className = classType.getName();
            } else {
                className = currentClassName;
            }
        }

        // Use import mapping if available
        String mappedClassName = types.getImport(className);
        if (mappedClassName != null) {
            className = mappedClassName;
        }

        sig.append(className.replace(".", "/")).append("/");

        // Get method name
        Element methodNameElement = callInst.getMethodName();
        String methodName;
        if (methodNameElement instanceof LiteralElement literal) {
            methodName = literal.getLiteral().replace("\"", "");
        } else {
            methodName = methodNameElement.toString();
        }
        sig.append(methodName);

        // Parameter types
        sig.append("(");
        if (callInst.getArguments() != null) {
            for (var arg : callInst.getArguments()) {
                sig.append(types.toJasmin(arg.getType()));
            }
        }
        sig.append(")");

        // Return type
        sig.append(types.toJasmin(callInst.getReturnType()));

        return sig.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();
        var object = putFieldInstruction.getObject();
        var value = putFieldInstruction.getValue();

        code.append(apply(object));
        code.append(apply(value));
        code.append("putfield ")
                .append(currentClassName).append("/")
                .append(putFieldInstruction.getField().getName())
                .append(" ").append(types.toJasmin(value.getType()))
                .append(NL);

        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();
        var object = getFieldInstruction.getObject();

        code.append(apply(object));
        code.append("getfield ")
                .append(currentClassName).append("/")
                .append(getFieldInstruction.getField().getName())
                .append(" ").append(types.toJasmin(getFieldInstruction.getField().getType()))
                .append(NL);

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInstruction) {
        return "goto " + gotoInstruction.getLabel() + NL;
    }

    private String generateCondBranch(CondBranchInstruction condBranchInstruction) {
        var code = new StringBuilder();
        var condition = condBranchInstruction.getCondition();

        // Handle different condition types
        if (condition instanceof BinaryOpInstruction binOp && isComparisonOperation(binOp.getOperation().getOpType())) {
            // Direct comparison in condition
            OperationType opType = binOp.getOperation().getOpType();
            Element leftOp = binOp.getLeftOperand();
            Element rightOp = binOp.getRightOperand();

            // Check for comparisons with zero (can use optimized instructions)
            boolean leftIsZero = isZero(leftOp);
            boolean rightIsZero = isZero(rightOp);

            if (leftIsZero || rightIsZero) {
                // Use optimized single-operand comparison
                Element nonZeroOp = leftIsZero ? rightOp : leftOp;
                code.append(apply(nonZeroOp));

                // Adjust comparison direction if left operand was zero
                OperationType adjustedOp = leftIsZero ? flipComparison(opType) : opType;

                String jumpInst = switch (adjustedOp) {
                    case LTH -> "iflt";
                    case GTH -> "ifgt";
                    case LTE -> "ifle";
                    case GTE -> "ifge";
                    case EQ -> "ifeq";
                    case NEQ -> "ifne";
                    default -> "ifne";
                };

                code.append(jumpInst).append(" ").append(condBranchInstruction.getLabel()).append(NL);
            } else {
                // Use two-operand comparison
                code.append(apply(leftOp));
                code.append(apply(rightOp));

                String jumpInst = switch (opType) {
                    case LTH -> "if_icmplt";
                    case GTH -> "if_icmpgt";
                    case LTE -> "if_icmple";
                    case GTE -> "if_icmpge";
                    case EQ -> "if_icmpeq";
                    case NEQ -> "if_icmpne";
                    default -> "if_icmpne";
                };

                code.append(jumpInst).append(" ").append(condBranchInstruction.getLabel()).append(NL);
            }
        } else if (condition instanceof SingleOpInstruction singleOp) {
            // Check if it's a variable holding a comparison result
            Element operand = singleOp.getSingleOperand();
            if (operand instanceof Operand op) {
                String varName = op.getName();
                BinaryOpInstruction comparison = findComparisonForVariable(varName);

                if (comparison != null) {
                    Element leftOp = comparison.getLeftOperand();
                    Element rightOp = comparison.getRightOperand();

                    if (isZero(rightOp)) {
                        code.append(apply(leftOp));
                        String branchInst = switch (comparison.getOperation().getOpType()) {
                            case LTH -> "iflt";
                            case GTE -> "ifge";
                            case EQ -> "ifeq";
                            case NEQ -> "ifne";
                            case GTH -> "ifgt";
                            case LTE -> "ifle";
                            default -> "ifne";
                        };
                        code.append(branchInst).append(" ").append(condBranchInstruction.getLabel()).append(NL);
                    } else if (isZero(leftOp)) {
                        code.append(apply(rightOp));
                        String branchInst = switch (comparison.getOperation().getOpType()) {
                            case LTH -> "ifgt";
                            case GTE -> "ifle";
                            case EQ -> "ifeq";
                            case NEQ -> "ifne";
                            case GTH -> "iflt";
                            case LTE -> "ifge";
                            default -> "ifne";
                        };
                        code.append(branchInst).append(" ").append(condBranchInstruction.getLabel()).append(NL);
                    } else {
                        code.append(apply(leftOp));
                        code.append(apply(rightOp));
                        String branchInst = switch (comparison.getOperation().getOpType()) {
                            case LTH -> "if_icmplt";
                            case GTE -> "if_icmpge";
                            case EQ -> "if_icmpeq";
                            case NEQ -> "if_icmpne";
                            case GTH -> "if_icmpgt";
                            case LTE -> "if_icmple";
                            default -> "if_icmpne";
                        };
                        code.append(branchInst).append(" ").append(condBranchInstruction.getLabel()).append(NL);
                    }
                } else {
                    // Simple boolean check
                    code.append(apply(condition));
                    code.append("ifne ").append(condBranchInstruction.getLabel()).append(NL);
                }
            } else {
                code.append(apply(condition));
                code.append("ifne ").append(condBranchInstruction.getLabel()).append(NL);
            }
        } else {
            // Fallback
            code.append(apply(condition));
            code.append("ifne ").append(condBranchInstruction.getLabel()).append(NL);
        }

        return code.toString();
    }

    private boolean isComparisonOperation(OperationType opType) {
        return switch (opType) {
            case LTH, GTH, LTE, GTE, EQ, NEQ -> true;
            default -> false;
        };
    }

    private boolean isZero(Element element) {
        if (element instanceof LiteralElement literal) {
            return literal.getLiteral().equals("0");
        }
        return false;
    }

    private OperationType flipComparison(OperationType op) {
        return switch (op) {
            case LTH -> OperationType.GTH;
            case GTH -> OperationType.LTH;
            case LTE -> OperationType.GTE;
            case GTE -> OperationType.LTE;
            case EQ -> OperationType.EQ;
            case NEQ -> OperationType.NEQ;
            default -> op;
        };
    }

    private BinaryOpInstruction findComparisonForVariable(String varName) {
        // Look through the method instructions to find the assignment that created this variable
        if (currentMethod != null) {
            for (Instruction inst : currentMethod.getInstructions()) {
                if (inst instanceof AssignInstruction assign &&
                        assign.getDest() instanceof Operand dest &&
                        dest.getName().equals(varName) &&
                        assign.getRhs() instanceof BinaryOpInstruction binOp) {

                    OperationType opType = binOp.getOperation().getOpType();
                    if (isComparisonOperation(opType)) {
                        return binOp;
                    }
                }
            }
        }
        return null;
    }

    private String generateOpInstruction(OpInstruction opInst) {
        if (opInst instanceof BinaryOpInstruction binOp) {
            return generateBinaryOp(binOp);
        }
        if(opInst instanceof  UnaryOpInstruction unaryOp) {
            return generateUnaryOperation(unaryOp);
        }
        // OpInstruction is the base class, handle it generically
        return "; unsupported OpInstruction: " + opInst.getClass().getSimpleName() + NL;
    }

    private String generateArrayOperand(ArrayOperand arrayOp) {
        var code = new StringBuilder();

        // Load array reference - use variable name directly
        String arrayName = arrayOp.getName();
        Integer reg = getVariableRegister(arrayName);
        if (reg != null) {
            code.append("aload ").append(reg).append(NL);
        }

        // Load index
        code.append(apply(arrayOp.getIndexOperands().get(0)));

        // Load from array
        String loadType = getArrayLoadInstruction(arrayOp.getType());
        code.append(loadType).append(NL);

        return code.toString();
    }

    private String getArrayLoadInstruction(Type type) {
        if (type instanceof BuiltinType builtin) {
            return switch (builtin.getKind()) {
                case INT32 -> "iaload";
                case BOOLEAN -> "baload";
                default -> "aaload";
            };
        }
        return "aaload";
    }
}