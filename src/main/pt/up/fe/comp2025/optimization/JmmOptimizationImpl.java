package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.ConfigOptions;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.backend.RegisterAllocator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        // Check if optimizations are enabled
        boolean optimize = ConfigOptions.getOptimize(semanticsResult.getConfig());
        if (!optimize) {
            return semanticsResult;
        }

        // Create a list to store reports
        List<Report> reports = new ArrayList<>(semanticsResult.getReports());

        // We don't need to create a copy of the root node, we'll optimize it directly
        var rootNode = semanticsResult.getRootNode();

        // Apply constant propagation visitor
        var constantPropVisitor = new ConstantPropagationVisitor(semanticsResult.getSymbolTable());

        // Apply constant folding visitor
        var constantFoldVisitor = new ConstantFoldingVisitor();

        // Apply optimizations until we reach a fixed point
        boolean changed;
        do {
            changed = false;

            // Apply constant propagation
            Boolean propResult = constantPropVisitor.visit(rootNode);
            if (propResult != null && propResult) {
                changed = true;
            }

            // Apply constant folding
            Boolean foldResult = constantFoldVisitor.visit(rootNode);
            if (foldResult != null && foldResult) {
                changed = true;
            }

        } while (changed);

        // Return the optimized semantics result
        return new JmmSemanticsResult(rootNode, semanticsResult.getSymbolTable(), reports, semanticsResult.getConfig());
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        // Get register allocation config (if any)
        int registerAllocation = ConfigOptions.getRegisterAllocation(ollirResult.getConfig());

        // If register allocation is not enabled, just return the original result
        if (registerAllocation == -1) {
            return ollirResult;
        }

        // Parse the OLLIR code to get the class unit
        org.specs.comp.ollir.ClassUnit classUnit = ollirResult.getOllirClass();

        // Apply register allocation to each method in the class
        for (org.specs.comp.ollir.Method method : classUnit.getMethods()) {
            // Skip constructor methods
            if (method.isConstructMethod()) {
                continue;
            }

            // Create a register allocator for the method
            RegisterAllocator allocator = new RegisterAllocator(method, registerAllocation);

            try {
                // Allocate registers
                allocator.allocate();

                // Apply the mapping to the method's var table
                allocator.applyMapping();
            } catch (RuntimeException e) {
                // If allocation fails, report the error
                ollirResult.getReports().add(new Report(
                        ReportType.ERROR,
                        Stage.OPTIMIZATION,
                        -1, -1,
                        e.getMessage()
                ));

                return ollirResult;
            }
        }

        // Return the optimized OLLIR result (the changes have been made directly to the classUnit)
        return ollirResult;
    }
}