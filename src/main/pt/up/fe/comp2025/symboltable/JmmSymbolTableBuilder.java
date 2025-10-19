
// JmmSymbolTableBuilder.java — atualizado com verificações de varargs

package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {
        reports = new ArrayList<>();

        var classDecl = root.getChildren(CLASS_DECL).getFirst();
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("className");
        var superName = classDecl.hasAttribute("superName") ? classDecl.get("superName") : null;
        var methods = buildMethods(classDecl);
        var imports = buildImports(root);
        var returnTypes = buildReturnTypes(classDecl);
        var fields = buildFields(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(className, superName, methods, imports, returnTypes, fields, params, locals);
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fieldList = new ArrayList<>();

        for (var field : classDecl.getChildren(VAR_DECL)) {
            var fieldName = field.get("var");
            Type type = TypeUtils.convertType(field.getChild(0));

            if (Boolean.parseBoolean(field.getChild(0).get("isVararg"))) {
                reports.add(newError(field, "Field declarations cannot be vararg."));
            }

            if (fieldList.stream().anyMatch(s -> s.getName().equals(fieldName))) {
                reports.add(newError(classDecl, "Parameter " + fieldName + " already exists"));
            }

            fieldList.add(new Symbol(type, fieldName));
        }

        return fieldList;
    }

    private List<String> buildImports(JmmNode root) {
        List<String> imports = new ArrayList<>();

        for (var importDecl : root.getChildren(Kind.IMPORT_DECL)) {
            String fullImport = String.join(".", importDecl.getObjectAsList("importName", String.class));
            String className = importDecl.getObjectAsList("importName", String.class).getLast();

            if (imports.stream().anyMatch(s -> s.equals(className) || s.endsWith("." + className))) {
                reports.add(newError(root, "Import " + fullImport + " already exists"));
            }

            imports.add(fullImport);
        }

        return imports;
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("methodName");

            if (map.containsKey(name)) {
                reports.add(newError(classDecl, "Method " + name + " already exists"));
            }

            if (name.equals("main")) {
                map.put("main", new Type("void", false));
            } else {
                Type type = TypeUtils.convertType(method.getChild(0));

                if (Boolean.parseBoolean(method.getChild(0).get("isVararg"))) {
                    boolean add = reports.add(newError(method, "Method return types cannot be vararg."));
                }

                map.put(name, type);
            }
        }

        return map;
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("methodName");
            List<Symbol> paramList = new ArrayList<>();

            for (var param : method.getChildren(PARAM)) {
                var paramName = param.get("name");
                Type type;
                if (Boolean.parseBoolean(param.getChild(0).get("isVararg"))) {
                    type = new Type("...", true);
                }
                else {
                    type = TypeUtils.convertType(param.getChild(0));
                }
                if (paramList.stream().anyMatch(s -> s.getName().equals(paramName))) {
                    reports.add(newError(classDecl, "Parameter " + paramName + " already exists"));
                }
                paramList.add(new Symbol(type, paramName));
            }

            // Verifica se há mais de um vararg ou se o vararg não é o último
            boolean foundVararg = false;

            for (int i = 0; i < method.getChildren(PARAM).size(); i++) {
                var param = method.getChildren(PARAM).get(i);
                var isVararg = Boolean.parseBoolean(param.getChild(0).get("isVararg"));

                if (isVararg) {
                    if (foundVararg) {
                        reports.add(newError(param, "Only one vararg parameter is allowed per method."));
                    } else if (i != method.getChildren(PARAM).size() - 1) {
                        reports.add(newError(param, "Vararg parameter must be the last parameter in the method declaration."));
                    }

                    foundVararg = true;
                }
            }

            map.put(name, paramList);
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("methodName");
            List<Symbol> localsList = new ArrayList<>();

            for (var param : method.getChildren(VAR_DECL)) {
                var paramName = param.get("var");
                Type type = TypeUtils.convertType(param.getChild(0));

                if (Boolean.parseBoolean(param.getChild(0).get("isVararg"))) {
                    reports.add(newError(param, "Variable declarations cannot be vararg."));
                }

                if (localsList.stream().anyMatch(s -> s.getName().equals(paramName))) {
                    reports.add(newError(classDecl, "Parameter " + paramName + " already exists"));
                }

                localsList.add(new Symbol(type, paramName));
            }

            map.put(name, localsList);
        }

        return map;
    }

    private List<String> buildMethods(JmmNode classDecl) {
        var methods = classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("methodName"))
                .toList();

        return methods;
    }

}
