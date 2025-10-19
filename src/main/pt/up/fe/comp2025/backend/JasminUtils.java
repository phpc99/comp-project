package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.CallInstruction;
import org.specs.comp.ollir.inst.NewInstruction;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JasminUtils {

    private final OllirResult ollirResult;
    private final Map<String, String> importMapping = new HashMap<>();

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }

    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    public void buildImports(List<String> imports) {
        for (var imp : imports) {
            var importClass = imp.substring(imp.lastIndexOf(".") + 1);
            importMapping.put(importClass, imp.replace(".", "/"));
        }
    }

    public String getImport(String className) {
        return importMapping.get(className);
    }

    public String toJasmin(Type type) {

        if (type instanceof BuiltinType builtinType) return toJasmin(builtinType);
        if (type instanceof ArrayType arrayType) return toJasmin(arrayType);
        if (type instanceof ClassType classType) return toJasmin(classType);

        return toJasmin(type);
    }

    private String toJasmin(BuiltinType type) {
        return switch (type.getKind()) {
            case BuiltinKind.INT32 -> "I";
            case BuiltinKind.BOOLEAN -> "Z";
            case BuiltinKind.VOID -> "V";
            case BuiltinKind.STRING -> "Ljava/lang/String;";
        };
    }

    private String toJasmin(ArrayType type) {
        return "[" + toJasmin(type.getElementType());
    }

    private String toJasmin(ClassType type) {
        return "L" + importMapping.getOrDefault(type.getName(), type.getName()) + ";";
    }
}
