package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import static pt.up.fe.comp2025.ast.Kind.TYPE;
import static pt.up.fe.comp2025.ast.Kind.fromString;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {

    private final AccumulatorMap<String> temporaries;
    private int labelCounter = 0;

    private final TypeUtils types;

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
    }

    public String nextTemp() {
        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {
        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;
        return prefix + nextTempNum;
    }

    /**
     * Generates a unique label ID for control flow.
     * @return A unique integer for label generation
     */
    public int nextLabelId() {
        return ++labelCounter;
    }

    public String toOllirType(JmmNode typeNode) {
        TYPE.checkOrThrow(typeNode);
        return toOllirType(types.convertType(typeNode));
    }

    public String toOllirType(Type type) {
        if(type == null)
            return toOllirType("void",false);
        return toOllirType(type.getName(),type.isArray());
    }

    private String toOllirType(String typeName,Boolean isArray) {
        String type = isArray ? ".array." : ".";

        type += switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "string" -> "string";
            case "void" -> "V";
            case "..." -> "i32";
            default -> typeName;
        };
        return type;
    }
}