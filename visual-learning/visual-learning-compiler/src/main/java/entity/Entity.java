package entity;


import ast.ExprNode;
import ast.Location;
import ast.TypeNode;
import type.*;

abstract public class Entity implements ast.Dumpable {
    protected String name;
    protected boolean isPrivate;
    protected TypeNode typeNode;
    protected long nRefered;



    public Entity(boolean priv, TypeNode type, String name) {
        this.name = name;
        this.isPrivate = priv;
        this.typeNode = type;
        this.nRefered = 0;
    }

    public String name() {
        return name;
    }

    abstract public boolean isDefined();

    public boolean isConstant() { return false; }

    public ExprNode value() {
        throw new Error("Entity#value");
    }

    public boolean isParameter() { return false; }

    public boolean isPrivate() {
        return isPrivate;
    }

    public TypeNode typeNode() {
        return typeNode;
    }

    public Type type() {
        return typeNode.type();
    }


    public void refered() {
        nRefered++;
    }

    public boolean isRefered() {
        return (nRefered > 0);
    }



    public Location location() {
        return typeNode.location();
    }

    abstract public <T> T accept(EntityVisitor<T> visitor);

    public void dump(ast.Dumper d) {
        d.printClass(this, location());
        _dump(d);
    }

    abstract protected void _dump(ast.Dumper d);
}
