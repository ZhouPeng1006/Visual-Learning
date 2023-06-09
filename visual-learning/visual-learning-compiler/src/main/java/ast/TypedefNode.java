package ast;
import type.*;

public class TypedefNode extends TypeDefinition {
    protected TypeNode real;

    public TypedefNode(Location loc, TypeRef real, String name) {
        super(loc, new UserTypeRef(name), name);
        this.real = new TypeNode(real);
    }


    public TypeNode realTypeNode() {
        return real;
    }




    protected void _dump(Dumper d) {
        d.printMember("name", name);
        d.printMember("typeNode", typeNode);
    }

    public <T> T accept(DeclarationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
