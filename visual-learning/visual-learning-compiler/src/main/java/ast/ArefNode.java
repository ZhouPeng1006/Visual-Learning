package ast;
import type.*;

public class ArefNode extends LHSNode {
    private ExprNode expr, index;

    public ArefNode(ExprNode expr, ExprNode index) {
        this.expr = expr;
        this.index = index;
    }

    public ExprNode expr() { return expr; }
    public ExprNode index() { return index; }

    public boolean isMultiDimension() {
        return (expr instanceof ArefNode) && !expr.origType().isPointer();
    }

    public long length() {
        return ((ArrayType)expr.origType()).length();
    }

    protected Type origType() {
        return expr.origType().baseType();
    }

    public Location location() {
        return expr.location();
    }

    protected void _dump(Dumper d) {
        if (type != null) {
            d.printMember("type", type);
        }
        d.printMember("expr", expr);
        d.printMember("index", index);
    }

    public <S,E> E accept(ASTVisitor<S,E> visitor) {
        return visitor.visit(this);
    }
}
