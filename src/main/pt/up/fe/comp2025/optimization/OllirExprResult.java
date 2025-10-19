package pt.up.fe.comp2025.optimization;

public class OllirExprResult {

    public static final OllirExprResult EMPTY = new OllirExprResult("", "");

    private final String computation;
    private final String reference;

    public OllirExprResult(String reference, String computation) {
        this.reference = reference;
        this.computation = computation;
    }

    public OllirExprResult(String reference) {
        this(reference, "");
    }

    public OllirExprResult(String reference, StringBuilder computation) {
        this(reference, computation.toString());
    }

    public String getComputation() {
        return computation;
    }

    public String getReference() {
        return reference;
    }

    @Override
    public String toString() {
        return "OllirNodeResult{" +
                "computation='" + computation + '\'' +
                ", reference='" + reference + '\'' +
                '}';
    }
}
