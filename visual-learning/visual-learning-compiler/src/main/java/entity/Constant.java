package entity;

import ast.ExprNode;
import ast.TypeNode;

public class Constant extends Entity {
    private ExprNode value;

    public Constant(TypeNode type, String name, ExprNode value) {
        super(true, type, name);
        this.value = value;
    }

    public boolean isDefined() { return true; }
    public boolean isInitialized() { return true; }
    public boolean isConstant() { return true; }

    public ExprNode value() { return value; }

    protected void _dump(ast.Dumper d) {
        d.printMember("name", name);
        d.printMember("typeNode", typeNode);
        d.printMember("value", value);
    }

    public <T> T accept(EntityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
