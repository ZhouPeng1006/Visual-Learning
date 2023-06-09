package entity;
import ast.TypeNode;

public class Parameter extends DefinedVariable {
    public Parameter(TypeNode type, String name) {
        super(false, type, name, null);
    }

    public boolean isParameter() {
        return true;
    }

    protected void _dump(ast.Dumper d) {
        d.printMember("name", name);
        d.printMember("typeNode", typeNode);
    }
}
